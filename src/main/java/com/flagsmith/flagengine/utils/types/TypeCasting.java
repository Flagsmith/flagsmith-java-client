package com.flagsmith.flagengine.utils.types;

import com.flagsmith.flagengine.segments.constants.SegmentConditions;

public class TypeCasting {

  /**
   * Compare the values value1 and value2 with the provided condition.
   * @param condition SegmentCondition criteria to compare values against.
   * @param value1 Value to compare.
   * @param value2 Value to compare against.
   * @return
   */
  public static Boolean compare(SegmentConditions condition, Object value1, Object value2) {

    if (TypeCasting.isInteger(value1) && TypeCasting.isInteger(value2)) {
      return compare(condition, TypeCasting.toInteger(value1), TypeCasting.toInteger(value2));
    } else if (TypeCasting.isFloat(value1) && TypeCasting.isFloat(value2)) {
      return compare(condition, TypeCasting.toFloat(value1), TypeCasting.toFloat(value2));
    } else if (TypeCasting.isBoolean(value1) && TypeCasting.isBoolean(value2)) {
      return compare(condition, TypeCasting.toBoolean(value1), TypeCasting.toBoolean(value2));
    }

    return value1.equals(value2);
  }

  /**
   * Run comparison with condition of primitive type.
   * @param condition SegmentCondition criteria to compare values against.
   * @param value1 Value to compare.
   * @param value2 Value to compare against.
   * @return
   */
  public static Boolean compare(SegmentConditions condition, Comparable value1, Comparable value2) {
    if (condition.equals(SegmentConditions.EQUAL)) {
      return value1.compareTo(value2) == 0;
    } else if (condition.equals(SegmentConditions.GREATER_THAN)) {
      return value1.compareTo(value2) > 0;
    } else if (condition.equals(SegmentConditions.GREATER_THAN_INCLUSIVE)) {
      return value1.compareTo(value2) >= 0;
    } else if (condition.equals(SegmentConditions.LESS_THAN)) {
      return value1.compareTo(value2) < 0;
    } else if (condition.equals(SegmentConditions.LESS_THAN_INCLUSIVE)) {
      return value1.compareTo(value2) <= 0;
    } else if (condition.equals(SegmentConditions.NOT_EQUAL)) {
      return value1.compareTo(value2) != 0;
    } else if (condition.equals(SegmentConditions.NOT_CONTAINS)) {
      return value1.compareTo(value2) != 0;
    }

    return value1.compareTo(value2) == 0;
  }

  /**
   * Convert the object to float.
   * @param number Object to convert to Float.
   * @return
   */
  public static Float toFloat(Object number) {
    try {
      return Float.parseFloat((String) number);
    } catch (NumberFormatException nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Float?.
   * @param number Object to type check.
   * @return
   */
  public static Boolean isFloat(Object number) {
    return toFloat(number) != null;
  }

  /**
   * Convert to object to Integer.
   * @param number Object to convert to Integer.
   * @return
   */
  public static Integer toInteger(Object number) {
    try {
      return Integer.parseInt((String) number);
    } catch (NumberFormatException nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Integer?.
   * @param number Object to type check.
   * @return
   */
  public static Boolean isInteger(Object number) {
    return toInteger(number) != null;
  }

  /**
   * Convert the object to Boolean.
   * @param str Object to convert to Boolean.
   * @return
   */
  public static Boolean toBoolean(Object str) {
    try {
      String value = ((String) str).toLowerCase();
      return Boolean.parseBoolean(value);
    } catch (NumberFormatException nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Boolean?.
   * @param str Object to type check.
   * @return
   */
  public static Boolean isBoolean(Object str) {
    String value = ((String) str).toLowerCase();
    return Boolean.TRUE.toString().toLowerCase().equals(value)
        || Boolean.FALSE.toString().toLowerCase().equals(value);
  }
}
