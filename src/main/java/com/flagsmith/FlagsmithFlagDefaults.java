package com.flagsmith;

import com.flagsmith.interfaces.DefaultFlagHandler;
import com.flagsmith.models.DefaultFlag;
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
  public List<Flag> getDefaultFlags() {
    return this.defaultFeatureFlags.stream().map(this::createDefaultFlag).collect(
        Collectors.toList());
  }

  /**
   * It adds any default flags that may be missing from flagsAndTraits.getFlags().
   *
   * @param flagsAndTraits the user's flags and traits
   * @return flags and traits
   */
  public FlagsAndTraits enrichWithDefaultFlags(FlagsAndTraits flagsAndTraits) {
    if (flagsAndTraits.getFlags() == null) {
      flagsAndTraits.setFlags(new ArrayList<>());
    }

    final Set<String> flagsNotFound = defaultFeatureFlags.stream()
        .filter(defaultFlagName -> !isFlagWithNameFound(flagsAndTraits, defaultFlagName))
        .collect(Collectors.toSet());

    for (String flagNameToBeAdded : flagsNotFound) {
      flagsAndTraits.getFlags().add(createDefaultFlag(flagNameToBeAdded));
    }
    return flagsAndTraits;
  }

  private boolean isFlagWithNameFound(FlagsAndTraits flagsAndTraits, String defaultFlagName) {
    return flagsAndTraits.getFlags().stream()
        .anyMatch(existingFlag -> existingFlag.getFeature().getName().equals(defaultFlagName));
  }

  private Flag createDefaultFlag(String flagName) {
    final Flag flag = new Flag();
    flag.setEnabled(this.evaluateDefaultFlagPredicate(flagName));
    flag.setStateValue(this.evaluateDefaultFlagValue(flagName));

    final Feature feature = new Feature();
    feature.setName(flagName);
    flag.setFeature(feature);

    if (flag.getStateValue() == null) {
      feature.setType("FLAG");
    } else {
      feature.setType("CONFIG");
    }
    return flag;
  }

  /**
   * evaluate the default feature flag
   * @param featureName feature name
   * @return
   */
  public DefaultFlag evaluateDefaultFlag(String featureName) {
    final DefaultFlag flag = new DefaultFlag();
    flag.setEnabled(evaluateDefaultFlagPredicate(featureName));
    flag.setValue(evaluateDefaultFlagValue(featureName));
    flag.setFeatureName(featureName);
    flag.setIsDefault(Boolean.TRUE);

    return flag;
  }
}
