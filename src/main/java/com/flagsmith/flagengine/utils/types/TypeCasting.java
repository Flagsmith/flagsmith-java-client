package com.flagsmith.flagengine.utils.types;

import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.utils.SemanticVersioning;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;

public class TypeCasting {

  /**
   * Compare the values value1 and value2 with the provided condition.
   *
   * @param condition SegmentCondition criteria to compare values against.
   * @param value1 Value to compare.
   * @param value2 Value to compare against.
   */
  public static Boolean compare(SegmentConditions condition, Object value1, Object value2) {

    if (condition.equals(SegmentConditions.MODULO)) {
      return compareModulo(String.valueOf(value2), value1);
    }

    if (isInteger(value1) && isInteger(value2)) {
      return compare(condition, toInteger(value1), toInteger(value2));
    } else if (isFloat(value1) && isFloat(value2)) {
      return compare(condition, toFloat(value1), toFloat(value2));
    } else if (isDouble(value1) && isDouble(value2)) {
      return compare(condition, toDouble(value1), toDouble(value2));
    } else if (isBoolean(value1) && isBoolean(value2)) {
      return compare(condition, toBoolean(value1), toBoolean(value2));
    } else if (isSemver(value2)) {
      return compare(condition, toSemver(value1), toSemver(value2));
    }

    return compare(condition, (String) value1, (String) value2);
  }

  /**
   * Run comparison with condition of primitive type.
   *
   * @param condition SegmentCondition criteria to compare values against.
   * @param value1 Value to compare.
   * @param value2 Value to compare against.
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
   * Convert the object to Double.
   *
   * @param number Object to convert to Double.
   */
  public static Double toDouble(Object number) {
    try {
      String asString = String.valueOf(number);
      return number instanceof Double ? ((Double) number) : Double.parseDouble(asString);
    } catch (Exception nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Double?.
   *
   * @param number Object to type check.
   */
  public static Boolean isDouble(Object number) {
    return number instanceof Float || toDouble(number) != null;
  }

  /**
   * Convert the object to float.
   *
   * @param number Object to convert to Float.
   */
  public static Float toFloat(Object number) {
    try {
      return number instanceof Float ? ((Float) number) : Float.parseFloat(String.valueOf(number));
    } catch (Exception nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Float?.
   *
   * @param number Object to type check.
   */
  public static Boolean isFloat(Object number) {
    return number instanceof Float || toFloat(number) != null;
  }

  /**
   * Convert to object to Integer.
   *
   * @param number Object to convert to Integer.
   */
  public static Integer toInteger(Object number) {
    try {
      String asString = String.valueOf(number);
      return number instanceof Integer ? ((Integer) number) : Integer.valueOf(asString);
    } catch (Exception nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Integer?.
   *
   * @param number Object to type check.
   */
  public static Boolean isInteger(Object number) {
    return number instanceof Integer || toInteger(number) != null;
  }

  /**
   * Convert the object to Boolean.
   *
   * @param str Object to convert to Boolean.
   */
  public static Boolean toBoolean(Object str) {
    try {
      return str instanceof Boolean ? ((Boolean) str)
          : BooleanUtils.toBoolean((String) str);
    } catch (Exception nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Boolean?.
   *
   * @param str Object to type check.
   */
  public static Boolean isBoolean(Object str) {
    return str instanceof Boolean
        || Boolean.TRUE.toString().equalsIgnoreCase(((String) str))
        || Boolean.FALSE.toString().equalsIgnoreCase(((String) str));
  }

  /**
   * Convert the object to Semver.
   *
   * @param str Object to convert to Semver.
   */
  public static ComparableVersion toSemver(Object str) {
    try {
      String value = SemanticVersioning.isSemver((String) str)
          ? SemanticVersioning.removeSemver((String) str) : ((String) str);
      return new ComparableVersion(value);
    } catch (Exception nfe) {
      return null;
    }
  }

  /**
   * Is the object of type Semver?.
   *
   * @param str Object to type check.
   */
  public static Boolean isSemver(Object str) {
    return SemanticVersioning.isSemver((String) str);
  }

  /**
   * Modulo is a special case as the condition value holds both the divisor and remainder.
   * This method compares the conditionValue and the traitValue by dividing the traitValue
   * by the divisor and verifying that it correctly equals the remainder.
   *
   * @param conditionValue conditionValue in the format 'divisor|remainder'
   * @param traitValue the value of the matched trait
   * @return true if expression evaluates to true, false if unable to evaluate expression or
   *     it evaluates to false
   */
  public static Boolean compareModulo(String conditionValue, Object traitValue) {
    try {
      String[] divisorAndRemainder = conditionValue.split("\\|");
      Float divisor = toFloat(divisorAndRemainder[0]);
      Float remainder = toFloat(divisorAndRemainder[1]);

      return toFloat(traitValue) % divisor == remainder;
    } catch (NumberFormatException | NullPointerException e) {
      return false; // indicates that one of the values could not be case to Float.
    }
  }
}
