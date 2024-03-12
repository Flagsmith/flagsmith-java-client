package com.flagsmith.flagengine;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.utils.exceptions.FeatureStateNotFound;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Engine {

  /**
   * Get a list of feature states for a given environment.
   *
   * @param environment Instance of the Environment.
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
   * Get a specific feature state for a given feature_name in a given environment.
   *
   * @param environment Instance of the Environment.
   * @param featureName Feature name to search for.
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
   * @param environmentModel Instance of the Environment.
   * @param identityModel Instance of Identity.
   * @return
   */
  public static List<FeatureStateModel> getIdentityFeatureStates(EnvironmentModel environmentModel,
                                                                 IdentityModel identityModel) {
    return getIdentityFeatureStates(environmentModel, identityModel, null);
  }

  /**
   * Get a list of feature states for a given identity in a given environment.
   *
   * @param environmentModel Instance of the Environment.
   * @param identityModel Instance of Identity.
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

  public static FeatureStateModel getIdentityFeatureStateForFlag(
      EnvironmentModel environmentModel,
      IdentityModel identityModel,
      String featureName) {

    return getIdentityFeatureStateForFlag(environmentModel, identityModel, featureName, null);
  }

  /**
   * Get a specific feature state for a given identity in a given environment.
   * @param environmentModel Instance of the Environment.
   * @param identityModel Instance of identity.
   * @param overrideTraits Traits to override identity's traits.
   * @param featureName Feature Name to search for.
   */
  public static FeatureStateModel getIdentityFeatureStateForFlag(
      EnvironmentModel environmentModel,
      IdentityModel identityModel,
      String featureName,
      List<TraitModel> overrideTraits) {

    FeatureStateModel model = getIdentityFeature(environmentModel, identityModel,
        overrideTraits, featureName);

    if (environmentModel.getProject().getHideDisabledFlags()) {
      return model != null && model.getEnabled() ? model : null;
    }

    return model;
  }

  /**
   * Get a specific feature state for a given identity in a given environment.
   *
   * @param environmentModel Instance of the Environment.
   * @param identityModel Instance of identity.
   * @param featureName Feature Name to search for.
   * @param overrideTraits Traits to override identity's traits.
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
   * @param environmentModel Instance of the Environment.
   * @param identityModel Instance of identity.
   * @param overrideTraits Traits to override identity's traits.
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
        FeatureModel feature = featureState.getFeature();
        FeatureStateModel existing = featureStates.get(feature);
        if (existing != null && existing.isHigherPriority(featureState)) {
          continue;
        }

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

  private static FeatureStateModel getIdentityFeature(
      EnvironmentModel environmentModel,
      IdentityModel identityModel, List<TraitModel> overrideTraits,
      String featureName) {

    FeatureStateModel featureStateToReturn =
        environmentModel.getEnvironmentFeatureStates().get(featureName);

    List<SegmentModel> segments = environmentModel.getFeatureSegments()
        .getOrDefault(featureName, Collections.emptyList());

    List<FeatureStateModel> segmentFeatureStates = segments.stream()
        .filter(segmentModel -> SegmentEvaluator
            .evaluateIdentityInSegment(identityModel, segmentModel, overrideTraits))
        .flatMap(segmentModel -> segmentModel.getFeatureStates().stream()
            .filter(featureStateModel -> featureStateModel.getFeature().getName()
                .equals(featureName)))
        .collect(Collectors.toList());

    for (FeatureStateModel featureStateModel : segmentFeatureStates) {
      if (featureStateToReturn != null
          && featureStateToReturn.isHigherPriority(featureStateModel)) {
        continue;
      }
      featureStateToReturn = featureStateModel;
    }


    return featureStateToReturn;
  }
}
