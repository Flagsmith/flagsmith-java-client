package com.solidstategroup.bullettrain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class TraitDto {
    private String trait_key;
    private String trait_value;
    private FeatureUser identity;

    public TraitDto(FeatureUser user, Trait trait) {
        trait_key = trait.getKey();
        trait_value = trait.getValue();
        identity = user;
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
