package com.flagsmith.flagengine.segments.constants;

public enum SegmentConditions {
  EQUAL, GREATER_THAN, LESS_THAN, LESS_THAN_INCLUSIVE, CONTAINS,
  GREATER_THAN_INCLUSIVE, NOT_CONTAINS, NOT_EQUAL, REGEX, PERCENTAGE_SPLIT,
  MODULO, IS_SET, IS_NOT_SET, IN;
}
