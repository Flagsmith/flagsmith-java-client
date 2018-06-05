package com.ssg.bullettrain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;

/**
 * Json model representation of the FeatureFlag
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureFlag {
    private Long id;
    private String value;
    private boolean enabled;

    @JsonIgnore
    public void parse(String data) throws IOException {
        ObjectMapper mapper = MapperFactory.getMappper();
        FeatureFlag prototype = mapper.readValue(data, FeatureFlag.class);
        fromPrototype(prototype);
    }

    @JsonIgnore
    private void fromPrototype(FeatureFlag prototype) throws IOException {
        setId(prototype.getId());
        setValue(prototype.getValue());
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
