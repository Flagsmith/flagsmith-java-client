package com.flagsmith.flagengine.utils.models;

import java.lang.Class;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.flagsmith.flagengine.utils.encode.JSONEncoder;

public abstract class BaseModel {
    public static <T extends BaseModel> T load(JsonNode json, Class<T> clazz) {
        JsonMapper mapper = JSONEncoder.getMapper();
        try {
            return mapper.treeToValue(json, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

}
