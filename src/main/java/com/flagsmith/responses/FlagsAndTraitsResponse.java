package com.flagsmith.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.flagengine.features.FeatureStateModel;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagsAndTraitsResponse {
  List<FeatureStateModel> flags;
  JsonNode traits;
}
