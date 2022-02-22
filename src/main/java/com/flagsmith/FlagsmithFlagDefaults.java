package com.flagsmith;

import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.interfaces.DefaultFlagHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.NonNull;

public class FlagsmithFlagDefaults implements DefaultFlagHandler {

  private Predicate<String> defaultFlagPredicate = (String flagName) -> false;
  private Function<String, String> defaultFlagValueFunc = (String flagName) -> null;
  private Set<String> defaultFeatureFlags = new HashSet<>();

  public boolean evaluateDefaultFlagPredicate(String flagName) {
    return defaultFlagPredicate.test(flagName);
  }

  public void setDefaultFlagPredicate(@NonNull Predicate<String> defaultFlagPredicate) {
    this.defaultFlagPredicate = defaultFlagPredicate;
  }

  public String evaluateDefaultFlagValue(String flagName) {
    return defaultFlagValueFunc.apply(flagName);
  }

  public void setDefaultFlagValueFunc(@NonNull Function<String, String> defaultFlagValueFunc) {
    this.defaultFlagValueFunc = defaultFlagValueFunc;
  }

  public Set<String> getDefaultFeatureFlagNames() {
    return defaultFeatureFlags;
  }

  public void setDefaultFeatureFlags(@NonNull Set<String> defaultFeatureFlags) {
    this.defaultFeatureFlags = defaultFeatureFlags.stream().map(String::toLowerCase)
        .collect(Collectors.toSet());
  }

  /**
   * Get the default feature flags. This method can be useful for your unit tests, to ensure you
   * have setup the defaults correctly.
   *
   * @return list of default flags, not fetched from Flagsmith
   */
  public List<FeatureStateModel> getDefaultFlags() {
    return this.defaultFeatureFlags.stream().map(this::evaluateDefaultFlag).collect(
        Collectors.toList());
  }

  /**
   * It adds any default flags that may be missing from flagsAndTraits.getFlags().
   *
   * @param baseFlags the user's flags and traits
   * @return flags and traits
   */
  public List<FeatureStateModel> enrichWithDefaultFlags(List<FeatureStateModel> baseFlags) {
    if (baseFlags == null) {
      baseFlags = new ArrayList<>();
    }

    final List<FeatureStateModel> finalledBaseFlags = baseFlags;

    final Set<String> flagsNotFound = defaultFeatureFlags.stream()
        .filter(defaultFlagName -> !isFlagWithNameFound(finalledBaseFlags, defaultFlagName))
        .collect(Collectors.toSet());

    for (String flagNameToBeAdded : flagsNotFound) {
      baseFlags.add(evaluateDefaultFlag(flagNameToBeAdded));
    }
    return baseFlags;
  }

  private boolean isFlagWithNameFound(List<FeatureStateModel> baseFlag, String defaultFlagName) {
    return baseFlag.stream()
        .anyMatch(existingFlag -> existingFlag.getFeature().getName().equals(defaultFlagName));
  }

  /**
   * evaluate the default feature flag.
   * @param featureName feature name
   * @return
   */
  public FeatureStateModel evaluateDefaultFlag(String featureName) {
    final FeatureStateModel flag = new FeatureStateModel();
    flag.setEnabled(evaluateDefaultFlagPredicate(featureName));
    flag.setValue(evaluateDefaultFlagValue(featureName));

    FeatureModel featureModel = new FeatureModel();
    featureModel.setName(featureName);

    flag.setFeature(featureModel);

    return flag;
  }
}
