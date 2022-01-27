package com.flagsmith.flagengine.environments.integrations;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IntegrationModel {
    @JsonProperty("api_key")
    private String apiKey;
    @JsonProperty("base_url")
    private String baseUrl;
}
