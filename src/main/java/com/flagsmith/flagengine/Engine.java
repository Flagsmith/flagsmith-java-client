package com.flagsmith.flagengine;

import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.utils.Hashing;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class Engine {  
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
   * Get evaluation result for a given evaluation context.
   *
   * @param context Evaluation context.
   * @return Evaluation result.
   */
  public static EvaluationResult getEvaluationResult(EvaluationContext context) {
    enrichEvaluationContext(context);
    SegmentEvaluationResult segmentEvaluationResult = evaluateSegments(context);
    Flags flags = evaluateFeatures(context, segmentEvaluationResult.getSegmentFeatureContexts());

    return new EvaluationResult()
        .withFlags(flags)
        .withSegments(segmentEvaluationResult.getSegments());
  }

  private static void enrichEvaluationContext(EvaluationContext context) {
    IdentityContext identity = context.getIdentity();
    if (identity != null) {
      if (StringUtils.isEmpty(identity.getKey())) {
        identity.setKey(context.getEnvironment().getKey() + "_" + identity.getIdentifier());
      }
    }
  }

  private static SegmentEvaluationResult evaluateSegments(
      EvaluationContext context) {
    List<SegmentResult> segments = new ArrayList<>();
    HashMap<String, ImmutablePair<String, FeatureContext>> segmentFeatureContexts = new HashMap<>();

    Segments contextSegments = context.getSegments();

    if (contextSegments != null) {
      for (SegmentContext segmentContext : contextSegments.getAdditionalProperties().values()) {
        if (SegmentEvaluator.isContextInSegment(context, segmentContext)) {
          segments.add(new SegmentResult()
              .withName(segmentContext.getName())
              .withMetadata(segmentContext.getMetadata()));

          List<FeatureContext> segmentOverrides = segmentContext.getOverrides();

          if (segmentOverrides != null) {
            for (FeatureContext featureContext : segmentOverrides) {
              String featureName = featureContext.getName();

              if (segmentFeatureContexts.containsKey(featureName)) {
                ImmutablePair<String, FeatureContext> existing = segmentFeatureContexts
                    .get(featureName);
                FeatureContext existingFeatureContext = existing.getRight();

                Double existingPriority = existingFeatureContext.getPriority() == null
                    ? EngineConstants.WEAKEST_PRIORITY
                    : existingFeatureContext.getPriority();
                Double featurePriority = featureContext.getPriority() == null
                    ? EngineConstants.WEAKEST_PRIORITY
                    : featureContext.getPriority();

                if (existingPriority < featurePriority) {
                  continue;
                }
              }
              segmentFeatureContexts.put(featureName,
                  new ImmutablePair<String, FeatureContext>(
                      segmentContext.getName(), featureContext));
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

    String identityKey = context.getIdentity() != null
        ? context.getIdentity().getKey()
        : null;

    if (contextFeatures != null) {
      for (FeatureContext featureContext : contextFeatures.getAdditionalProperties().values()) {
        if (segmentFeatureContexts.containsKey(featureContext.getName())) {
          ImmutablePair<String, FeatureContext> segmentNameFeaturePair = segmentFeatureContexts
              .get(featureContext.getName());
          featureContext = segmentNameFeaturePair.getRight();
          flags.setAdditionalProperty(
              featureContext.getName(),
              new FlagResult().withEnabled(featureContext.getEnabled())
                  .withName(featureContext.getName())
                  .withValue(featureContext.getValue())
                  .withReason(
                      "TARGETING_MATCH; segment=" + segmentNameFeaturePair.getLeft())
                  .withMetadata(featureContext.getMetadata()));
        } else {
          flags.setAdditionalProperty(featureContext.getName(),
              getFlagResultFromFeatureContext(featureContext, identityKey));
        }
      }
    }

    return flags;
  }

  private static FlagResult getFlagResultFromFeatureContext(
      FeatureContext featureContext,
      String identityKey) {
    if (identityKey != null) {
      List<FeatureValue> variants = featureContext.getVariants();
      if (variants != null) {
        Float percentageValue = Hashing.getInstance()
            .getHashedPercentageForObjectIds(List.of(featureContext.getKey(), identityKey));

        Float startPercentage = 0.0f;

        ArrayList<FeatureValue> sortedVariants = new ArrayList<>(variants);
        sortedVariants.sort((a, b) -> {
          Double priority = a.getPriority();
          Double comparedPriority = b.getPriority();
          return priority.compareTo(comparedPriority);
        });
        for (FeatureValue variant : sortedVariants) {
          Double weight = variant.getWeight();
          Float limit = startPercentage + weight.floatValue();
          if (startPercentage <= percentageValue && percentageValue < limit) {
            return new FlagResult().withEnabled(featureContext.getEnabled())
                .withName(featureContext.getName())
                .withValue(variant.getValue())
                .withReason("SPLIT; weight=" + BigDecimal.valueOf(weight)
                  .stripTrailingZeros()
                  .toPlainString())
                .withMetadata(featureContext.getMetadata());
          }
          startPercentage = limit;
        }
      }
    }

    return new FlagResult().withEnabled(featureContext.getEnabled())
        .withName(featureContext.getName())
        .withValue(featureContext.getValue())
        .withReason("DEFAULT")
        .withMetadata(featureContext.getMetadata());
  }
}