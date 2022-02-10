package com.flagsmith.flagengine.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.utils.models.BaseModel;
import java.util.UUID;
import lombok.Data;

@Data
public class MultivariateFeatureStateValueModel extends BaseModel {
  @JsonProperty("multivariate_feature_option")
  private MultivariateFeatureOptionModel multivariateFeatureOption;
  @JsonProperty("percentage_allocation")
  private Float percentageAllocation;
  private Integer id;
  @JsonProperty("mv_fs_value_uuid")
  private String mvFsValueUuid = UUID.randomUUID().toString();

  public Comparable getSortValue() {
    return id != null ? id : mvFsValueUuid;
  }
}
