package com.flagsmith.flagengine.segments;

import java.util.List;
import java.util.stream.Stream;
import static com.flagsmith.flagengine.segments.constants.SegmentRules.*;

import lombok.Data;

@Data
public class SegmentRuleModel {
    private String type;
    private List<SegmentRuleModel> rules;
    private List<SegmentConditionModel> conditions;

    public Boolean matchingFunction(Stream<Boolean> booleanStream) {
            if(ALL_RULE.getRule().equals(type)) {
                return booleanStream.allMatch((bool) -> bool);
            } else if(ANY_RULE.getRule().equals(type)) {
                return booleanStream.anyMatch((bool) -> bool);
            } else if(NONE_RULE.getRule().equals(type)) {
                return !booleanStream.anyMatch((bool) -> bool);
            }

            return false;
    }
}
