package com.flagsmith.flagengine.environments;

import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.segments.SegmentModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * A model that provides faster, optimized access to the environment modeld.
 */
@Data
public class OptimizedAccessEnvironmentModel {

  /**
   * Converts an {@link EnvironmentModel} to an {@link OptimizedAccessEnvironmentModel}.
   */
  public static OptimizedAccessEnvironmentModel fromEnvironmentModel(
      EnvironmentModel environmentModel) {
    OptimizedAccessEnvironmentModel optimizedAccessEnvironmentModel =
        new OptimizedAccessEnvironmentModel();

    optimizedAccessEnvironmentModel.environmentModel = environmentModel;

    optimizedAccessEnvironmentModel.environmentFeatureStates = environmentModel
        .getFeatureStates().stream()
        .collect(Collectors.toMap(
            fs -> fs.getFeature().getName(),
            featureState -> featureState)
        );

    optimizedAccessEnvironmentModel.featureSegments = new HashMap<>();

    environmentModel
        .getProject().getSegments()
        .forEach(segment ->
            segment.getFeatureStates().forEach(
                featureState ->
                    optimizedAccessEnvironmentModel.featureSegments
                        .compute(featureState.getFeature().getName(), (k, v) -> {
                          if (v == null) {
                            v = new ArrayList<>();
                          }
                          v.add(segment);
                          return v;
                        }))
    );

    return optimizedAccessEnvironmentModel;
  }

  EnvironmentModel environmentModel;

  /**
   * An O(1) fast lookup for feature states in the environment by flag name.
   */
  Map<String, FeatureStateModel> environmentFeatureStates;

  /**
   * An O(1) fast lookup for segments that have custom feature states for a given flag name.
   */
  Map<String, List<SegmentModel>> featureSegments;
}
