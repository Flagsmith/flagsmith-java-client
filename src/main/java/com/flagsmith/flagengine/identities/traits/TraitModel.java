package com.flagsmith.flagengine.identities.traits;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraitModel {
  @JsonProperty("trait_key")
  private String traitKey;
  @JsonProperty("trait_value")
  private Object traitValue;
}
