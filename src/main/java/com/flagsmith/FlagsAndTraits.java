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
 * Holds a list of feature flags and user traits.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlagsAndTraits implements Serializable {

  private List<Flag> flags;
  private List<Trait> traits;

  /**
   * Parses given string into FlagsAndTraits object.
   *
   * @param data a json string representing FlagsAndTraits
   * @throws IOException when failed to parse data
   */
  @JsonIgnore
  public void parse(String data) throws IOException {
    ObjectMapper mapper = MapperFactory.getMappper();
    FlagsAndTraits prototype = mapper.readValue(data, FlagsAndTraits.class);
    fromPrototype(prototype);
  }

  @JsonIgnore
  private void fromPrototype(FlagsAndTraits prototype) {
    setFlags(prototype.getFlags());
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
