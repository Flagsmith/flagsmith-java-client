package com.flagsmith.flagengine.segments;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import com.flagsmith.flagengine.segments.constants.SegmentRules;
import static com.flagsmith.flagengine.segments.constants.SegmentRules.*;

import lombok.Data;

@Data
public class SegmentRuleModel {
    private String type;
    private List<SegmentRuleModel> rules;
    private List<SegmentConditionModel> conditions;

    public Boolean matchingFunction(Stream<SegmentConditionModel> booleanStream, Predicate<SegmentConditionModel> predicate) {
            if(ALL_RULE.getRule().equals(type)) {
                return booleanStream.allMatch(predicate);
            } else if(ANY_RULE.getRule().equals(type)) {
                return booleanStream.anyMatch(predicate);
            } else if(NONE_RULE.getRule().equals(type)) {
                return !booleanStream.anyMatch(predicate);
            }

            return false;
    }
}
