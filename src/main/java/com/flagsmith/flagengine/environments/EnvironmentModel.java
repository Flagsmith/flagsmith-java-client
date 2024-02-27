package com.flagsmith.flagengine.environments;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.environments.integrations.IntegrationModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.projects.ProjectModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.utils.models.BaseModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;


@Data
public class EnvironmentModel extends BaseModel {
  private Integer id;

  @JsonProperty("api_key")
  private String apiKey;
  private ProjectModel project;

  @JsonProperty("feature_states")
  private List<FeatureStateModel> featureStates;

  @JsonProperty("amplitude_config")
  private IntegrationModel amplitudeConfig;
  @JsonProperty("segment_config")
  private IntegrationModel segmentConfig;
  @JsonProperty("mixpanel_config")
  private IntegrationModel mixpanelConfig;
  @JsonProperty("heap_config")
  private IntegrationModel heapConfig;


  /**
   * An O(1) fast lookup for feature states in the environment by flag name.
   */
  @JsonIgnore
  Map<String, FeatureStateModel> environmentFeatureStates;

  /**
   * An O(1) fast lookup for segments that have custom feature states for a given flag name.
   */
  @JsonIgnore
  Map<String, List<SegmentModel>> featureSegments;

  /**
   * Initialize the cache for the environment. This allows O(1) access to environment feature
   * states using {@link EnvironmentModel#getEnvironmentFeatureStates()} and feature segments using
   * {@link EnvironmentModel#getFeatureSegments()} ()}.
   */
  public void initializeCache() {
    environmentFeatureStates = featureStates.stream()
        .collect(Collectors.toMap(
            fs -> fs.getFeature().getName(),
            featureState -> featureState)
        );

    featureSegments = new HashMap<>();

    project
        .getSegments()
        .forEach(segment -> {
          if (segment.getFeatureStates() != null) {
            segment.getFeatureStates().forEach(
                featureState ->
                    featureSegments
                        .compute(featureState.getFeature().getName(), (k, v) -> {
                          if (v == null) {
                            v = new ArrayList<>();
                          }
                          v.add(segment);
                          return v;
                        }));
          }
        }
      );
  }
}
