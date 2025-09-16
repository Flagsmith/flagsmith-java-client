package com.flagsmith.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.utils.models.BaseModel;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class FeatureStateModel extends BaseModel {
  @Data
  public class FeatureModel {
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("type")
    private String type;
  }

  @Data
  public class MultivariateFeatureOptionModel {
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("value")
    private String value;
  }

  @Data
  public class FeatureSegmentModel {
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("priority")
    private Integer priority;
  }

  @Data
  public class MultivariateFeatureStateValueModel {
    @JsonProperty("multivariate_feature_option")
    private MultivariateFeatureOptionModel multivariateFeatureOption;
    @JsonProperty("percentage_allocation")
    private Float percentageAllocation;
    @JsonProperty("id")
    private Integer id;

    public Float getSortValue() {
      return percentageAllocation != null ? percentageAllocation : 0f;
    }
  }

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
