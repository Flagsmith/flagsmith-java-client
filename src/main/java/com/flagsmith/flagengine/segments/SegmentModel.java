package com.flagsmith.flagengine.segments;

import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

import java.util.List;

@Data
public class SegmentModel extends BaseModel {
    private Integer id;
    private String name;
    private List<SegmentRuleModel> rules;
    private List<FeatureStateModel> featureStates;
}
