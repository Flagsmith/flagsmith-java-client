package com.flagsmith.flagengine.segments;

import com.flagsmith.flagengine.segments.constants.SegmentRules;

import java.util.List;
import java.util.stream.Stream;

import lombok.Data;

@Data
public class SegmentRuleModel {
  private String type;
  private List<SegmentRuleModel> rules;
  private List<SegmentConditionModel> conditions;

  /**
   * Run the matching function against the boolean stream.
   * @param booleanStream Boolean stream from trait condition evaluations.
   * @return
   */
  public Boolean matchingFunction(Stream<Boolean> booleanStream) {
    if (SegmentRules.ALL_RULE.getRule().equals(type)) {
      return booleanStream.allMatch((bool) -> bool);
    } else if (SegmentRules.ANY_RULE.getRule().equals(type)) {
      return booleanStream.anyMatch((bool) -> bool);
    } else if (SegmentRules.NONE_RULE.getRule().equals(type)) {
      return !booleanStream.anyMatch((bool) -> bool);
    }

    return false;
  }
}
