package com.flagsmith;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import java.io.IOException;
import java.io.Serializable;
import lombok.Data;

/**
 * Representation of the user trait model.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraitRequest extends TraitModel implements Serializable {

  private IdentityModel identity;

  @JsonProperty("trait_key")
  private String key;
  @JsonProperty("trait_value")
  private String value;

  /**
   * Default constructor.
   */
  public TraitRequest() {
  }

  /**
   * All arguments constructor.
   *
   * @param identity an Identity user
   * @param key      a trait key
   * @param value    a trait value
   */
  public TraitRequest(IdentityModel identity, String key, String value) {
    this.identity = identity;
    this.key = key;
    this.value = value;
  }

  /**
   * Trait Request Constructor.
   *
   * @param trait an Identity user
   */
  public TraitRequest(TraitModel trait) {
    this.key = trait.getTraitKey();
    this.value = getTraitValue();
  }

  /**
   * Parses given string into Trait object.
   *
   * @param data a json string representing Trait
   * @throws IOException when failed to parse data
   */
  @JsonIgnore
  public void parse(String data) throws IOException {
    ObjectMapper mapper = MapperFactory.getMappper();
    TraitRequest prototype = mapper.readValue(data, TraitRequest.class);
    fromPrototype(prototype);
  }

  @JsonIgnore
  private void fromPrototype(TraitRequest prototype) {
    setKey(prototype.getKey());
    setValue(prototype.getValue());
    setIdentity(prototype.getIdentity());
  }

  @JsonIgnore
  @Override
  public String toString() {
    ObjectMapper mapper = MapperFactory.getMappper();
    try {
      return mapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return super.toString();
    }
  }
}
