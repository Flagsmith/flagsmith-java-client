package com.flagsmith.flagengine.segments;

import java.util.List;
import lombok.Data;

@Data
public class SegmentRuleModel {
    private String type;
    private List<SegmentRuleModel> rules;
    private List<SegmentConditionModel> conditions;
}
