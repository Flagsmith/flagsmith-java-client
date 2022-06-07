package com.flagsmith.flagengine.organisations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class OrganisationModel extends BaseModel {
  private Integer id;
  private String name;
  @JsonProperty("feature_analytics")
  private Boolean featureAnalytics;
  @JsonProperty("stop_serving_flags")
  private Boolean stopServingFlags;
  @JsonProperty("persist_trait_data")
  private Boolean persistTraitData;

  public String uniqueSlug() {
    return id.toString() + "-" + name;
  }
}
