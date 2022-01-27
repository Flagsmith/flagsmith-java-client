package com.flagsmith.flagengine.utils.types;

import com.flagsmith.flagengine.segments.constants.SegmentConditions;

import java.util.Locale;

public class TypeCasting {

    public static Boolean compare(SegmentConditions condition, Object value1, Object value2) {
        if (TypeCasting.isFloat(value1) && TypeCasting.isFloat(value2)) {
            return compare(condition, TypeCasting.toFloat(value1), TypeCasting.toFloat(value2));
        } else if (TypeCasting.isInteger(value1) && TypeCasting.isInteger(value2)) {
            return compare(condition, TypeCasting.toInteger(value1), TypeCasting.toInteger(value2));
        }  else if (TypeCasting.isBoolean(value1) && TypeCasting.isBoolean(value2)) {
            return compare(condition, TypeCasting.toBoolean(value1), TypeCasting.toBoolean(value2));
        }

        return value1.equals(value2);
    }

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

    public static Float toFloat(Object number) {
        try {
            return Float.parseFloat((String) number);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public static Boolean isFloat(Object number) {
        return toFloat(number) != null;
    }

    public static Integer toInteger(Object number) {
        try {
            return Integer.parseInt((String) number);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public static Boolean isInteger(Object number) {
        return toInteger(number) != null;
    }

    public static Boolean toBoolean(Object str) {
        try {
            return Boolean.parseBoolean(((String) str).toLowerCase(Locale.ROOT));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public static Boolean isBoolean(Object number) {
        return toBoolean(number) != null;
    }
}
