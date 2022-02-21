package com.flagsmith.interfaces;

import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.models.DefaultFlag;

public interface DefaultFlagHandler {

  public FeatureStateModel evaluateDefaultFlag(String featureName);

}
