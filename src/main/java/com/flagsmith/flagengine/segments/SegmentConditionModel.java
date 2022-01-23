package com.flagsmith.flagengine.segments;

import lombok.Data;

@Data
public class SegmentConditionModel {
    private String operator;
    private String value;
    private String property_;

    public Boolean matchesTraitValue() {
        return false;
    }

    private Boolean evaluateNotContains() {
        return false;
    }

    private Boolean evaluateRegex() {
        return false;
    }
}
