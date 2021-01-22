package com.flagsmith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class
 *
 * Created by Pavlo Maksymchuk.
 */
public class Utils {

    public static String toJson(Object object) {
        ObjectMapper mapper = MapperFactory.getMappper();
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert object to JSON string", e);
        }
    }
}
