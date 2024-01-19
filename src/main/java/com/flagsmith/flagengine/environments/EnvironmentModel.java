package com.flagsmith.flagengine.environments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.projects.ProjectModel;
import com.flagsmith.utils.models.BaseModel;
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

  @JsonProperty("identity_overrides")
  private List<IdentityModel> identityOverrides;
}
