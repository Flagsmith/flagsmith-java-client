package com.flagsmith.utils.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.MapperFactory;
import java.lang.Class;

public abstract class BaseModel {
  /**
   * Load the json node to object of class type T.
   * @param json JSON Node object.
   * @param clazz Class type of the intended targeted class/
   * @return
   */
  public static <T extends BaseModel> T load(JsonNode json, Class<T> clazz) {
    ObjectMapper mapper = MapperFactory.getMapper();
    try {
      return mapper.treeToValue(json, clazz);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

}
