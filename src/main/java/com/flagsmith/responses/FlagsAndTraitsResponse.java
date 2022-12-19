package com.flagsmith.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.flagengine.features.FeatureStateModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagsAndTraitsResponse {
    List<FeatureStateModel> flags;
    JsonNode traits;
}
