package com.flagsmith;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * DTO for wrapping user identifier and list of user Traits.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdentityTraits implements Serializable {

  private String identifier;
  private List<Trait> traits;

  /**
   * Parses given string into IdentityTraits object.
   *
   * @param data a json string representing IdentityTraits
   * @throws IOException when failed to parse data
   */
  @JsonIgnore
  public void parse(String data) throws IOException {
    ObjectMapper mapper = MapperFactory.getMappper();
    IdentityTraits prototype = mapper.readValue(data, IdentityTraits.class);
    fromPrototype(prototype);
  }

  @JsonIgnore
  private void fromPrototype(IdentityTraits prototype) {
    setIdentifier(prototype.getIdentifier());
    setTraits(prototype.getTraits());
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
