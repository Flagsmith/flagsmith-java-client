package com.flagsmith.flagengine;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.utils.exceptions.FeatureStateNotFound;

import java.util.List;
import java.util.stream.Collectors;

public class Engine {

    /**
     * Get a list of feature states for a given environment
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
     * @param environment
     * @param featureName
     * @return
     */
    public static FeatureStateModel getEnvironmentFeatureState(EnvironmentModel environment, String featureName)
            throws FeatureStateNotFound {
        return environment.getFeatureStates()
                .stream()
                .filter((featureState) -> featureState
                        .getFeature()
                        .getName()
                        .equals(featureName))
                .findFirst().orElseThrow(() -> new FeatureStateNotFound());
    }

    public static List<FeatureStateModel> getIdentityFeatureStates(EnvironmentModel environmentModel, IdentityModel identityModel) {
        return null;
    }
    void getIdentityFeatureState() {

    }
    private void _get_identity_feature_states_dict() {

    }
}