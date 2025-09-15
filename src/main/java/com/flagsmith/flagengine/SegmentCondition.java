package com.flagsmith.flagengine;

import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import java.util.List;
import lombok.Data;

@Data
public class SegmentCondition {
  private SegmentConditions operator;
  private Object value;
  private String property;

  /**
   * No args constructor for use in serialization.
   */
  public SegmentCondition() {
  }

  /**
   * Copy constructor.
   *
   * @param source the object being copied
   */
  public SegmentCondition(SegmentCondition source) {
    super();
    this.operator = source.operator;
    this.value = source.value;
    this.property = source.property;
  }

  /**
   * Constructor with all fields.
   *
   * @param operator the segment condition operator
   * @param property the property name
   * @param value    the condition value
   */
  public SegmentCondition(SegmentConditions operator, String property, Object value) {
    this.operator = operator;
    this.property = property;
    this.value = value;
  }

  /**
   * Set String value.
   *
   * @param value New String value.
   */
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * Set List value.
   *
   * @param value New List value.
   */
  public void setValue(List<String> value) {
    if (this.operator != SegmentConditions.IN) {
      throw new IllegalArgumentException("List value can only be set for IN operator");
    }
    this.value = value;
  }

  /**
   * Fluent setter for operator.
   *
   * @param operator the segment condition operator
   * @return the SegmentCondition instance
   */
  public SegmentCondition withOperator(SegmentConditions operator) {
    this.operator = operator;
    return this;
  }

  /**
   * Fluent setter for property.
   *
   * @param property the property name
   * @return the SegmentCondition instance
   */
  public SegmentCondition withProperty(String property) {
    this.property = property;
    return this;
  }

  /**
   * Fluent setter for value.
   *
   * @param value the condition value
   * @return the SegmentCondition instance
   */
  public SegmentCondition withValue(Object value) {
    this.value = value;
    return this;
  }

  /**
   * Fluent setter for String value.
   *
   * @param value the String condition value
   * @return the SegmentCondition instance
   */
  public SegmentCondition withValue(String value) {
    this.value = value;
    return this;
  }

  /**
   * Fluent setter for List value.
   *
   * @param value the List condition value
   * @return the SegmentCondition instance
   * @throws IllegalArgumentException if operator is not IN
   */
  public SegmentCondition withValue(List<String> value) {
    if (this.operator != SegmentConditions.IN) {
      throw new IllegalArgumentException("List value can only be set for IN operator");
    }
    this.value = value;
    return this;
  }
}
