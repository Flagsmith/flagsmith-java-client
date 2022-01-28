package com.flagsmith.flagengine;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.utils.exceptions.FeatureStateNotFound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Engine {

  /**
   * Get a list of feature states for a given environment
   *
   * @param environment
   * @return
   */
  public static List<FeatureStateModel> getEnvironmentFeatureStates(EnvironmentModel environment) {
    if (environment.getProject().getHideDisabledFlags()) {
      return environment.getFeatureStates()
          .stream()
          .filter((featureState) -> featureState.getEnabled())
          .collect(Collectors.toList());
    }
    return environment.getFeatureStates();
  }

  /**
   * Get a specific feature state for a given feature_name in a given environment
   *
   * @param environment
   * @param featureName
   * @return
   */
  public static FeatureStateModel getEnvironmentFeatureState(EnvironmentModel environment,
                                                             String featureName)
      throws FeatureStateNotFound {
    return environment.getFeatureStates()
        .stream()
        .filter((featureState) -> featureState
            .getFeature()
            .getName()
            .equals(featureName))
        .findFirst().orElseThrow(() -> new FeatureStateNotFound());
  }

  /**
   * Get a list of feature states for a given identity in a given environment.
   *
   * @param environmentModel
   * @param identityModel
   * @return
   */
  public static List<FeatureStateModel> getIdentityFeatureStates(EnvironmentModel environmentModel,
                                                                 IdentityModel identityModel) {
    return getIdentityFeatureStates(environmentModel, identityModel, null);
  }

  /**
   * Get a list of feature states for a given identity in a given environment.
   *
   * @param environmentModel
   * @param identityModel
   * @return
   */
  public static List<FeatureStateModel> getIdentityFeatureStates(EnvironmentModel environmentModel,
                                                                 IdentityModel identityModel,
                                                                 List<TraitModel> overrideTraits) {
    List<FeatureStateModel> featureStates =
        getIdentityFeatureMap(environmentModel, identityModel, overrideTraits)
            .values().stream().collect(Collectors.toList());

    if (environmentModel.getProject().getHideDisabledFlags()) {
      return featureStates
          .stream()
          .filter((featureState) -> featureState.getEnabled())
          .collect(Collectors.toList());
    }
    return featureStates;
  }

  /**
   * Get a specific feature state for a given identity in a given environment.
   *
   * @param environmentModel
   * @param identityModel
   * @param featureName
   * @param overrideTraits
   */
  public static FeatureStateModel getIdentityFeatureState(EnvironmentModel environmentModel,
                                                          IdentityModel identityModel,
                                                          String featureName,
                                                          List<TraitModel> overrideTraits)
      throws FeatureStateNotFound {
    Map<FeatureModel, FeatureStateModel> featureStates =
        getIdentityFeatureMap(environmentModel, identityModel, overrideTraits);

    FeatureModel feature = featureStates.keySet()
        .stream()
        .filter((featureModel) -> featureModel.getName().equals(featureName))
        .findFirst().orElseThrow(() -> new FeatureStateNotFound());

    return featureStates.get(feature);
  }

  /**
   * Build a feature map with feature as key and feature state as value.
   *
   * @param environmentModel
   * @param identityModel
   * @param overrideTraits
   * @return
   */
  private static Map<FeatureModel, FeatureStateModel> getIdentityFeatureMap(
      EnvironmentModel environmentModel,
      IdentityModel identityModel, List<TraitModel> overrideTraits) {

    Map<FeatureModel, FeatureStateModel> featureStates = new HashMap<>();

    if (environmentModel.getFeatureStates() != null) {
      featureStates = environmentModel.getFeatureStates()
          .stream()
          .collect(Collectors.toMap(
              FeatureStateModel::getFeature,
              (featureState) -> featureState)
          );
    }

    List<SegmentModel> identitySegments =
        SegmentEvaluator.getIdentitySegments(environmentModel, identityModel, overrideTraits);

    for (SegmentModel segmentModel : identitySegments) {
      for (FeatureStateModel featureState : segmentModel.getFeatureStates()) {
        featureStates.put(featureState.getFeature(), featureState);
      }
    }

    for (FeatureStateModel featureState : identityModel.getIdentityFeatures()) {
      if (featureStates.containsKey(featureState.getFeature())) {
        featureStates.put(featureState.getFeature(), featureState);
      }
    }

    return featureStates;
  }
}