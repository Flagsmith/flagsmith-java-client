package com.flagsmith;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Serializable;
import lombok.Data;

/**
 * Representation of the feature model of the feature Flag.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Feature implements Serializable {

  private String name;
  private String type; // either CONFIG or FLAG
  private String description;

  /**
   * Parses given string into Feature object.
   *
   * @param data a json string representing Feature
   * @throws IOException when failed to parse data
   */
  @JsonIgnore
  public void parse(String data) throws IOException {
    ObjectMapper mapper = MapperFactory.getMappper();
    Feature prototype = mapper.readValue(data, Feature.class);
    fromPrototype(prototype);
  }

  @JsonIgnore
  private void fromPrototype(Feature prototype) {
    setName(prototype.getName());
    setType(prototype.getType());
    setDescription(prototype.getDescription());
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
