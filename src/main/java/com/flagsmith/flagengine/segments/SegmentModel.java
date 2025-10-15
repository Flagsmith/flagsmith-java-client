package com.flagsmith.flagengine.segments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.utils.models.BaseModel;
import java.util.List;
import lombok.Data;

@Data
public class SegmentModel extends BaseModel {
  private Integer id;
  private String name;
  private List<SegmentRuleModel> rules;
  @JsonProperty("feature_states")
  private List<FeatureStateModel> featureStates;
}
