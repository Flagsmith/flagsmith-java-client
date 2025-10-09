package com.flagsmith.flagengine;

import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.utils.Hashing;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class Engine {
  /**
   * Get evaluation result for a given evaluation context.
   *
   * @param context Evaluation context.
   * @return Evaluation result.
   */
  public static EvaluationResult getEvaluationResult(EvaluationContext context) {
    List<SegmentResult> segments = new ArrayList<>();
    HashMap<String, ImmutablePair<String, FeatureContext>> segmentFeatureContexts = new HashMap<>();
    Flags flags = new Flags();

    for (SegmentContext segmentContext : context.getSegments().getAdditionalProperties().values()) {
      if (SegmentEvaluator.isContextInSegment(context, segmentContext)) {
        segments.add(new SegmentResult().withKey(segmentContext.getKey())
            .withName(segmentContext.getName()));

        List<FeatureContext> segmentOverrides = segmentContext.getOverrides();

        if (segmentOverrides != null) {
          for (FeatureContext featureContext : segmentOverrides) {
            String featureKey = featureContext.getFeatureKey();

            if (segmentFeatureContexts.containsKey(featureKey)) {
              ImmutablePair<String, FeatureContext> existing = segmentFeatureContexts
                  .get(featureKey);
              FeatureContext existingFeatureContext = existing.getRight();

              Double existingPriority = existingFeatureContext.getPriority() == null
                  ? Double.POSITIVE_INFINITY
                  : existingFeatureContext.getPriority();
              Double featurePriority = featureContext.getPriority() == null
                  ? Double.POSITIVE_INFINITY
                  : featureContext.getPriority();

              if (existingPriority < featurePriority) {
                continue;
              }
            }
            segmentFeatureContexts.put(featureKey,
                new ImmutablePair<String, FeatureContext>(
                    segmentContext.getName(), featureContext));
          }
        }
      }
    }

    String identityKey = context.getIdentity() != null
        ? context.getIdentity().getKey()
        : null;

    Features contextFeatures = context.getFeatures();
    if (contextFeatures != null) {
      for (FeatureContext featureContext : contextFeatures.getAdditionalProperties().values()) {
        if (segmentFeatureContexts.containsKey(featureContext.getFeatureKey())) {
          ImmutablePair<String, FeatureContext> segmentNameFeaturePair = segmentFeatureContexts
              .get(featureContext.getFeatureKey());
          featureContext = segmentNameFeaturePair.getRight();
          flags.setAdditionalProperty(
              featureContext.getName(),
              new FlagResult().withEnabled(featureContext.getEnabled())
                  .withFeatureKey(featureContext.getFeatureKey())
                  .withName(featureContext.getName())
                  .withValue(featureContext.getValue())
                  .withReason(
                      "TARGETING_MATCH; segment=" + segmentNameFeaturePair.getLeft()));
        } else {
          flags.setAdditionalProperty(featureContext.getName(),
              getFlagResultFromFeatureContext(featureContext, identityKey));
        }
      }
    }

    return new EvaluationResult().withFlags(flags).withSegments(segments);
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

        for (FeatureValue variant : variants) {
          Double weight = variant.getWeight();
          Float limit = startPercentage + weight.floatValue();
          if (startPercentage <= percentageValue && percentageValue < limit) {
            return new FlagResult().withEnabled(featureContext.getEnabled())
                .withFeatureKey(featureContext.getFeatureKey())
                .withName(featureContext.getName())
                .withValue(variant.getValue())
                .withReason("SPLIT; weight=" + weight.intValue());
          }
          startPercentage = limit;
        }
      }
    }

    return new FlagResult().withEnabled(featureContext.getEnabled())
        .withFeatureKey(featureContext.getFeatureKey())
        .withName(featureContext.getName())
        .withValue(featureContext.getValue())
        .withReason("DEFAULT");
  }
}