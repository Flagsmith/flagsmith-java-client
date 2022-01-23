package com.flagsmith.flagengine.data;

import lombok.Data;

import java.util.List;

@Data
public class FeatureStates {
    private List<FeatureState> featureStates;
}
