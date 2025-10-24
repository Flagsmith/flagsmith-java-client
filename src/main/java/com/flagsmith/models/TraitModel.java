package com.flagsmith.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TraitModel {
  @JsonProperty("trait_key")
  private String traitKey;
  @JsonProperty("trait_value")
  private Object traitValue;
}
