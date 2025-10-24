package com.flagsmith.models.identities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.models.features.FeatureStateModel;
import com.flagsmith.utils.models.BaseModel;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class IdentityModel extends BaseModel {
  @JsonProperty("django_id")
  private Integer djangoId;
  private String identifier;
  @JsonProperty("created_date")
  private Date createdDate;
  @JsonProperty("identity_uuid")
  private String identityUuid = UUID.randomUUID().toString();
  @JsonProperty("identity_features")
  private List<FeatureStateModel> identityFeatures = new ArrayList<>();
}