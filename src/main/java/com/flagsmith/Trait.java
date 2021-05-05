package com.flagsmith;


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
 * Representation of the user trait model.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trait implements Serializable {
    private FeatureUser identity;

    @JsonProperty("trait_key")
    private String key;
    @JsonProperty("trait_value")
    private String value;

    public Trait() {
    }

    public Trait(FeatureUser identity, String key, String value) {
        this.identity = identity;
        this.key = key;
        this.value = value;
    }

    @JsonIgnore
    public void parse(String data) throws IOException {
        ObjectMapper mapper = MapperFactory.getMappper();
        Trait prototype = mapper.readValue(data, Trait.class);
        fromPrototype(prototype);
    }

    @JsonIgnore
    private void fromPrototype(Trait prototype) {
        setKey(prototype.getKey());
        setValue(prototype.getValue());
        setIdentity(prototype.getIdentity());
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
