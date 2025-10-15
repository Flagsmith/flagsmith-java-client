package com.flagsmith.flagengine.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.utils.models.BaseModel;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class FeatureStateModel extends BaseModel {
  private FeatureModel feature;
  private Boolean enabled;
  @JsonProperty("django_id")
  private Integer djangoId;
  @JsonProperty("featurestate_uuid")
  private String featurestateUuid = UUID.randomUUID().toString();
  @JsonProperty("multivariate_feature_state_values")
  private List<MultivariateFeatureStateValueModel> multivariateFeatureStateValues;
  @JsonProperty("feature_state_value")
  private Object value;
  @JsonProperty("feature_segment")
  private FeatureSegmentModel featureSegment;
}
