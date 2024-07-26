package com.flagsmith.flagengine.identities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.utils.models.BaseModel;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;

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
  private List<? extends TraitModel> identityTraits = new ArrayList<>();
  @JsonProperty("identity_features")
  private List<FeatureStateModel> identityFeatures = new ArrayList<>();
  @JsonProperty("composite_key")
  private String compositeKey;

  /**
   * Returns the composite key for the identity.
   */
  public String getCompositeKey() {
    if (compositeKey == null) {
      compositeKey = environmentApiKey + "_" + identifier;
    }
    return compositeKey;
  }

  /**
   * Update the identity traits.
   *
   * @param traits traits to update
   */
  public void updateTraits(List<? extends TraitModel> traits) {
    Map<String, TraitModel> existingTraits = new HashMap<>();

    if (identityTraits != null && identityTraits.size() > 0) {
      existingTraits = identityTraits.stream()
          .collect(Collectors.toMap(TraitModel::getTraitKey, (trait) -> trait));
    }

    for (TraitModel trait : traits) {
      if (trait.getTraitValue() == null) {
        existingTraits.remove(trait.getTraitKey());
      } else {
        existingTraits.put(trait.getTraitKey(), trait);
      }
    }

    identityTraits = existingTraits.values()
        .stream().collect(Collectors.toList());
  }
}
