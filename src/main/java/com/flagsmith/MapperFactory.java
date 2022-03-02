package com.flagsmith;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for object mapper instances.
 */
public class MapperFactory {

  private static volatile ObjectMapper mapper = null;

  /**
   * Get default ObjectMapper.
   *
   * @return an ObjectMapper
   */
  public static ObjectMapper getMapper() {
    if (null == mapper) {
      mapper = new ObjectMapper();
      mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
      mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    return mapper;
  }
}
