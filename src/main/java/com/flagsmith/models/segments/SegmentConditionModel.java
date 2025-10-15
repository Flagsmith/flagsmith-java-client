package com.flagsmith.models.segments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import lombok.Data;

@Data
public class SegmentConditionModel {
  private SegmentConditions operator;
  private String value;
  @JsonProperty("property_")
  private String property;
}