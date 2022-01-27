package com.flagsmith.flagengine.identities.traits;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TraitModel {
    @JsonProperty("trait_key")
    private String traitKey;
    @JsonProperty("trait_value")
    private String traitValue;
}
