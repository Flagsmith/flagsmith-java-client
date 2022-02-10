package com.flagsmith.flagengine.environments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.environments.integrations.IntegrationModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.projects.ProjectModel;
import com.flagsmith.flagengine.utils.models.BaseModel;
import java.util.List;
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
}
