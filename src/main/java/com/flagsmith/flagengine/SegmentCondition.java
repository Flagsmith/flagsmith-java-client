package com.flagsmith.flagengine;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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
   * Set JsonNode value. Required for engine tests.
   *
   * @param node the JsonNode value.
   * @throws IllegalArgumentException if value is not a String or List of Strings
   */
  @JsonSetter("value")
  public void setValue(JsonNode node) {
    if (node.isArray()) {
      ArrayNode arr = (ArrayNode) node;
      List<String> values = StreamSupport.stream(arr.spliterator(), false)
          .peek(el -> {
            if (!el.isTextual()) {
              throw new IllegalArgumentException("Array elements must be strings");
            }
          })
          .map(JsonNode::asText)
          .collect(Collectors.toList());
      this.setValue(values);
    } else if (node.isTextual()) {
      this.setValue(node.asText());
    } else {
      throw new IllegalArgumentException("Value must be a String or List of Strings");
    }
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
