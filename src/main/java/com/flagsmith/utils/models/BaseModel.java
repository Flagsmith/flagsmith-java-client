package com.flagsmith.utils.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.flagsmith.flagengine.utils.encode.JsonEncoder;
import java.lang.Class;

public abstract class BaseModel {
  /**
   * Load the json node to object of class type T.
   * @param json JSON Node object.
   * @param clazz Class type of the intended targeted class/
   * @return
   */
  public static <T extends BaseModel> T load(JsonNode json, Class<T> clazz) {
    JsonMapper mapper = JsonEncoder.getMapper();
    try {
      return mapper.treeToValue(json, clazz);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

}
