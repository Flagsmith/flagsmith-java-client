package com.flagsmith.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_DEFAULT)
public class SdkTraitModel extends TraitModel {
  @JsonProperty("transient")
  @Builder.Default
  private Boolean isTransient = false;
}
