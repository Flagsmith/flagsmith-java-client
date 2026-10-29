package com.flagsmith.flagengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.utils.Hashing;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class Engine {
  public static final String CIRCULAR_DEPENDENCY_REASON = "ERROR; code=CIRCULAR_DEPENDENCY";

  private static final ObjectMapper mapper = new ObjectMapper();

  private static class SegmentEvaluationResult {
    List<SegmentResult> segments;
    HashMap<String, ImmutablePair<String, FeatureContext>> segmentFeatureContexts;

    public SegmentEvaluationResult(
        List<SegmentResult> segments,
        HashMap<String, ImmutablePair<String, FeatureContext>> segmentFeatureContexts) {
      this.segments = segments;
      this.segmentFeatureContexts = segmentFeatureContexts;
    }

    public List<SegmentResult> getSegments() {
      return segments;
    }

    public HashMap<String, ImmutablePair<String, FeatureContext>> getSegmentFeatureContexts() {
      return segmentFeatureContexts;
    }
  }

  /**
   * The `$.flags` mapping, resolving a flag when a condition first reads it.
   */
  private static class LazyFlags extends AbstractMap<String, Object> {
    private final EvaluationContext context;
    private final Map<String, FlagResult> resolved = new LinkedHashMap<>();
    private DependencyResolver resolver;

    /** Names of the flags found to be in a dependency cycle. */
    private final Set<String> cyclic = new HashSet<>();

    /** Whether a condition has read a flag, even one that resolved to nothing. */
    private boolean used = false;

    /** Incremented whenever a cycle is cut, so callers can tell a verdict rests on one. */
    private int cycleHits = 0;

    LazyFlags(EvaluationContext context) {
      this.context = context;
    }

    @Override
    public Object get(Object key) {
      if (!(key instanceof String)) {
        return null;
      }
      String featureName = (String) key;
      if (!resolved.containsKey(featureName)) {
        used = true;
        if (resolver == null) {
          resolver = new DependencyResolver(context, this);
        }
        resolver.resolveFeature(featureName);
      }
      FlagResult flagResult = resolved.get(featureName);
      return flagResult == null ? null : mapper.convertValue(flagResult, Map.class);
    }

    @Override
    public boolean containsKey(Object key) {
      return get(key) != null;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
      return resolved.keySet().stream()
          .map(featureName -> new SimpleImmutableEntry<>(featureName, get(featureName)))
          .collect(Collectors.toCollection(HashSet::new));
    }
  }

  /**
   * Resolves overrides for a context with flag dependencies, memoising the results.
   */
  private static class DependencyResolver {
    private final EvaluationContext context;
    private final LazyFlags flags;
    private final Map<String, Boolean> segmentMatches = new HashMap<>();
    private final List<String> resolving = new ArrayList<>();
    private final Map<String, List<String>> segmentKeysByFeatureName = new HashMap<>();

    DependencyResolver(EvaluationContext context, LazyFlags flags) {
      this.context = context;
      this.flags = flags;
      context.getSegments().getAdditionalProperties().forEach((segmentKey, segmentContext) -> {
        List<FeatureContext> overrides = segmentContext.getOverrides();
        if (overrides != null) {
          for (FeatureContext override : overrides) {
            segmentKeysByFeatureName
                .computeIfAbsent(override.getName(), name -> new ArrayList<>())
                .add(segmentKey);
          }
        }
      });
    }

    void resolveFeature(String featureName) {
      int cycleStart = resolving.indexOf(featureName);
      if (cycleStart != -1) {
        flags.cyclic.addAll(resolving.subList(cycleStart, resolving.size()));
        flags.cycleHits++;
        return;
      }
      Features features = context.getFeatures();
      FeatureContext featureContext = features == null
          ? null
          : features.getAdditionalProperties().get(featureName);
      if (featureContext == null) {
        return;
      }

      resolving.add(featureName);
      ImmutablePair<String, FeatureContext> segmentOverride;
      try {
        segmentOverride = getSegmentOverride(featureName);
      } finally {
        resolving.remove(resolving.size() - 1);
      }

      if (flags.cyclic.contains(featureName)) {
        return;
      }

      String identityKey = getIdentityKey(context);
      if (segmentOverride != null) {
        flags.resolved.put(featureName, getFlagResultFromFeatureContext(
            segmentOverride.getRight(),
            identityKey,
            "TARGETING_MATCH; segment=" + segmentOverride.getLeft()));
      } else {
        flags.resolved.put(featureName, getFlagResultFromFeatureContext(
            featureContext, identityKey, "DEFAULT"));
      }
    }

    private boolean matchesSegment(String segmentKey) {
      Boolean memoised = segmentMatches.get(segmentKey);
      if (memoised != null) {
        return memoised;
      }

      int cycleHits = flags.cycleHits;
      boolean matches = SegmentEvaluator.isContextInSegment(
          context, context.getSegments().getAdditionalProperties().get(segmentKey), flags);

      if (flags.cycleHits != cycleHits) {
        return false; // The verdict rests on a flag left unresolved by a cycle.
      }

      segmentMatches.put(segmentKey, matches);
      return matches;
    }

    private ImmutablePair<String, FeatureContext> getSegmentOverride(String featureName) {
      ImmutablePair<String, FeatureContext> segmentOverride = null;

      for (String segmentKey : segmentKeysByFeatureName.getOrDefault(featureName, List.of())) {
        if (!matchesSegment(segmentKey)) {
          continue;
        }
        SegmentContext segmentContext = context.getSegments().getAdditionalProperties()
            .get(segmentKey);
        for (FeatureContext overrideFeatureContext : segmentContext.getOverrides()) {
          if (!overrideFeatureContext.getName().equals(featureName)) {
            continue;
          }
          if (winsOver(overrideFeatureContext,
              segmentOverride == null ? null : segmentOverride.getRight())) {
            segmentOverride = new ImmutablePair<>(
                segmentContext.getName(), overrideFeatureContext);
          }
        }
      }

      return segmentOverride;
    }
  }

  /**
   * Get evaluation result for a given evaluation context.
   *
   * @param context Evaluation context.
   * @return Evaluation result.
   */
  public static EvaluationResult getEvaluationResult(EvaluationContext context) {
    context = getEnrichedEvaluationContext(context);
    LazyFlags resolved = new LazyFlags(context);
    SegmentEvaluationResult segmentEvaluationResult = evaluateSegments(context, resolved);
    Flags flags = evaluateFeatures(context, segmentEvaluationResult.getSegmentFeatureContexts());

    if (resolved.used) {
      flags.getAdditionalProperties().putAll(resolved.resolved);
      for (String featureName : resolved.cyclic) {
        FlagResult flagResult = flags.getAdditionalProperties().get(featureName);
        if (flagResult != null) {
          flagResult.setReason(CIRCULAR_DEPENDENCY_REASON);
        }
      }
    }

    return new EvaluationResult()
        .withFlags(flags)
        .withSegments(segmentEvaluationResult.getSegments());
  }

  /*
   * Get a version of the evaluation context enriched with derived data.
   *
   * @param context Evaluation context.
   * @return Enriched evaluation context, or the original if no enrichment was needed.
   */
  private static EvaluationContext getEnrichedEvaluationContext(EvaluationContext context) {
    IdentityContext identity = context.getIdentity();
    if (identity != null) {
      if (StringUtils.isEmpty(identity.getKey())) {
        String identityKey = context.getEnvironment().getKey() + "_" + identity.getIdentifier();
        context = new EvaluationContext(context).withIdentity(
          new IdentityContext(identity).withKey(identityKey));
      }
    }
    return context;
  }

  /*
   * Whether a segment override takes precedence over the one held so far.
   *
   * Lower priority wins, and the first seen wins a tie, so that precedence
   * follows context order rather than the order segments are evaluated in.
   */
  private static boolean winsOver(FeatureContext candidate, FeatureContext incumbent) {
    return incumbent == null || getPriority(candidate) < getPriority(incumbent);
  }

  private static double getPriority(FeatureContext featureContext) {
    return featureContext.getPriority() == null
        ? EngineConstants.WEAKEST_PRIORITY
        : featureContext.getPriority();
  }

  private static SegmentEvaluationResult evaluateSegments(
      EvaluationContext context, LazyFlags flags) {
    List<SegmentResult> segments = new ArrayList<>();
    HashMap<String, ImmutablePair<String, FeatureContext>> segmentFeatureContexts = new HashMap<>();

    Segments contextSegments = context.getSegments();

    if (contextSegments != null) {
      int cycleHits = flags.cycleHits;

      for (SegmentContext segmentContext : contextSegments.getAdditionalProperties().values()) {
        boolean matches = SegmentEvaluator.isContextInSegment(context, segmentContext, flags);

        if (flags.cycleHits != cycleHits) {
          cycleHits = flags.cycleHits; // The verdict rests on a flag left unresolved by a cycle.
          continue;
        }

        if (matches) {
          segments.add(new SegmentResult()
              .withName(segmentContext.getName())
              .withMetadata(segmentContext.getMetadata()));

          List<FeatureContext> segmentOverrides = segmentContext.getOverrides();

          if (segmentOverrides != null) {
            for (FeatureContext featureContext : segmentOverrides) {
              String featureName = featureContext.getName();
              ImmutablePair<String, FeatureContext> incumbent = segmentFeatureContexts
                  .get(featureName);

              if (winsOver(featureContext, incumbent == null ? null : incumbent.getRight())) {
                segmentFeatureContexts.put(featureName,
                    new ImmutablePair<String, FeatureContext>(
                        segmentContext.getName(), featureContext));
              }
            }
          }
        }
      }
    }

    return new SegmentEvaluationResult(segments, segmentFeatureContexts);
  }

  private static Flags evaluateFeatures(
      EvaluationContext context,
      HashMap<String, ImmutablePair<String, FeatureContext>> segmentFeatureContexts) {
    Features contextFeatures = context.getFeatures();
    Flags flags = new Flags();

    String identityKey = getIdentityKey(context);

    if (contextFeatures != null) {
      for (FeatureContext featureContext : contextFeatures.getAdditionalProperties().values()) {
        String reason;
        if (segmentFeatureContexts.containsKey(featureContext.getName())) {
          ImmutablePair<String, FeatureContext> segmentNameFeaturePair = segmentFeatureContexts
              .get(featureContext.getName());
          featureContext = segmentNameFeaturePair.getRight();
          reason = "TARGETING_MATCH; segment=" + segmentNameFeaturePair.getLeft();
        } else {
          reason = "DEFAULT";
        }
        flags.setAdditionalProperty(featureContext.getName(),
            getFlagResultFromFeatureContext(
                featureContext,
                identityKey,
                reason));
      }
    }

    return flags;
  }

  private static String getIdentityKey(EvaluationContext context) {
    return context.getIdentity() != null
        ? context.getIdentity().getKey()
        : null;
  }

  private static FlagResult getFlagResultFromFeatureContext(
      FeatureContext featureContext,
      String identityKey,
      String reason) {
    Object value = featureContext.getValue();
    String variant = null;

    List<FeatureValue> variants = featureContext.getVariants();
    if (identityKey != null && variants != null && !variants.isEmpty()) {
      variant = "control"; // A matched variant overrides the control bucket.

      Float percentageValue = Hashing.getInstance()
          .getHashedPercentageForObjectIds(List.of(featureContext.getKey(), identityKey));

      Float startPercentage = 0.0f;

      ArrayList<FeatureValue> sortedVariants = new ArrayList<>(variants);
      sortedVariants.sort((a, b) -> {
        Double priority = a.getPriority();
        Double comparedPriority = b.getPriority();
        return priority.compareTo(comparedPriority);
      });
      for (FeatureValue featureValue : sortedVariants) {
        Double weight = featureValue.getWeight();
        Float limit = startPercentage + weight.floatValue();
        if (startPercentage <= percentageValue && percentageValue < limit) {
          value = featureValue.getValue();
          variant = featureValue.getKey();
          reason = "SPLIT; weight=" + BigDecimal.valueOf(weight)
              .stripTrailingZeros()
              .toPlainString();
          break;
        }
        startPercentage = limit;
      }
    }

    return new FlagResult().withEnabled(featureContext.getEnabled())
        .withName(featureContext.getName())
        .withValue(value)
        .withReason(reason)
        .withVariant(variant)
        .withMetadata(featureContext.getMetadata());
  }
}
