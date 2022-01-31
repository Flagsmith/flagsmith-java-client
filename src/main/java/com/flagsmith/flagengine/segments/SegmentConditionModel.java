package com.flagsmith.flagengine.segments;

import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import lombok.Data;

@Data
public class SegmentConditionModel {
  private SegmentConditions operator;
  private String value;
  //CHECKSTYLE:OFF
  private String property_;
  //CHECKSTYLE:ON
}
