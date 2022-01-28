package com.flagsmith.flagengine.identities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.utils.models.BaseModel;
import java.sql.Date;
import lombok.Data;

import java.util.*;

@Data
public class IdentityModel extends BaseModel {
  @JsonProperty("django_id")
  private Integer djangoId;
  private String identifier;
  @JsonProperty("environment_api_key")
  private String environmentApiKey;
  @JsonProperty("created_date")
  private Date createdDate;
  @JsonProperty("identity_uuid")
  private String identityUuid = UUID.randomUUID().toString();
  @JsonProperty("identity_traits")
  private List<TraitModel> identityTraits = new ArrayList<>();
  @JsonProperty("identity_features")
  private Set<FeatureStateModel> identityFeatures = new HashSet<>();
  @JsonProperty("composite_key")
  private String compositeKey;

  public String getCompositeKey() {
    if (compositeKey == null) {
      compositeKey = environmentApiKey + "_" + identifier;
    }
    return compositeKey;
  }
}
