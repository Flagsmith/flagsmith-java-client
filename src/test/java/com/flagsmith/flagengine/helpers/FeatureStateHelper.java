package com.flagsmith.flagengine.helpers;

import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;

import java.util.List;

public class FeatureStateHelper {

  public static FeatureStateModel getFeatureStateForFeature(List<FeatureStateModel> featureStates,
                                                            FeatureModel feature) {
    return featureStates
        .stream()
        .filter((featureState) -> featureState.getFeature().equals(feature))
        .findFirst().orElse(null);
  }

  public static FeatureStateModel getFeatureStateForFeatureByName(
      List<FeatureStateModel> featureStates, String name) {
    return featureStates
        .stream()
        .filter((featureState) -> featureState.getFeature().getName().equals(name))
        .findFirst().orElse(null);
  }
}
