package com.flagsmith.flagengine.identities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
public class IdentityModel {
    @JsonProperty("django_id")
    private Integer djangoId;
    private String identifier;
    @JsonProperty("environment_api_key")
    private String environmentApiKey;
    @JsonProperty("created_date")
    private Instant createdDate;
    @JsonProperty("identity_uuid")
    private String identityUuid;
    @JsonProperty("identity_traits")
    private List<TraitModel> identityTraits;
    @JsonProperty("identity_features")
    private Set<FeatureStateModel> identityFeatures;
    @JsonProperty("composite_key")
    private String compositeKey;

    public String getCompositeKey() {
        if (compositeKey == null) {
            compositeKey = environmentApiKey + "_" +identifier;
        }
        return compositeKey;
    }
}
