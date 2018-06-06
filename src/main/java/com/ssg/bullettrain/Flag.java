package com.ssg.bullettrain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;
import java.io.Serializable;

/**
 * Representation of the feature flag model model representation of the Feature
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Flag implements Serializable {
    private Feature feature;
    @JsonProperty("feature_state_value")
    private String stateValue;
    private boolean enabled;

    @JsonIgnore
    public void parse(String data) throws IOException {
        ObjectMapper mapper = MapperFactory.getMappper();
        Flag prototype = mapper.readValue(data, Flag.class);
        fromPrototype(prototype);
    }

    @JsonIgnore
    private void fromPrototype(Flag prototype) throws IOException {
        setFeature(prototype.getFeature());
        setStateValue(prototype.getStateValue());
        setEnabled(prototype.isEnabled());
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
