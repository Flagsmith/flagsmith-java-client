package com.flagsmith.flagengine.utils.encode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class JsonEncoder {

  private static JsonMapper mapper;

  /**
   * Get the JSON Mapper object.
   * @return
   */
  public static JsonMapper getMapper() {
    if (mapper == null) {
      mapper = new JsonMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    }
    return mapper;
  }
}
