package com.flagsmith.flagengine.data;

import com.flagsmith.flagengine.features.FeatureModel;
import lombok.Data;

@Data
public class FeatureState {
    private FeatureModel feature;
    private Boolean enabled;

    public Object getValue(Object identifier) {
        return null;
    }
}
