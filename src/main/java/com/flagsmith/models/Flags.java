package com.flagsmith.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.FlagsmithFlagDefaults;
import com.flagsmith.exceptions.FeatureNotFoundError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.EvaluationResult;
import com.flagsmith.interfaces.DefaultFlagHandler;
import com.flagsmith.threads.AnalyticsProcessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class Flags {
  private Map<String, BaseFlag> flags = new HashMap<>();
  private AnalyticsProcessor analyticsProcessor;
  private DefaultFlagHandler defaultFlagHandler;

  /**
   * Build flags object from list of feature states.
   *
   * @param featureStates list of feature states
   * @param analyticsProcessor instance of analytics processor
   */
  public static Flags fromFeatureStateModels(
      List<FeatureStateModel> featureStates,
      AnalyticsProcessor analyticsProcessor) {
    return fromFeatureStateModels(featureStates, analyticsProcessor, null);
  }

  /**
   * Build flags object from list of feature states.
   *
   * @param featureStates list of feature states
   * @param analyticsProcessor instance of analytics processor
   * @param defaultFlagHandler default flags (optional)
   */
  public static Flags fromFeatureStateModels(
      List<FeatureStateModel> featureStates,
      AnalyticsProcessor analyticsProcessor,
      DefaultFlagHandler defaultFlagHandler) {

    Map<String, BaseFlag> flagMap = featureStates.stream()
        .collect(
            Collectors.toMap(
                (fs) -> fs.getFeature().getName(),
                (fs) -> Flag.fromFeatureStateModel(fs)));

    Flags flags = new Flags();
    flags.setFlags(flagMap);
    flags.setAnalyticsProcessor(analyticsProcessor);
    flags.setDefaultFlagHandler(defaultFlagHandler);

    return flags;
  }

  /**
   * Return the flags instance.
   *
   * @param apiFlags Dictionary with api flags
   * @param analyticsProcessor instance of analytics processor
   * @param defaultFlagHandler handler for default flags if present
   */
  public static Flags fromApiFlags(
      JsonNode apiFlags,
      AnalyticsProcessor analyticsProcessor,
      FlagsmithFlagDefaults defaultFlagHandler) {

    Map<String, BaseFlag> flagMap = new HashMap<>();

    for (JsonNode node : apiFlags) {
      flagMap.put(
          node.get("feature").get("name").asText(),
          Flag.fromApiFlag(node));
    }

    Flags flags = new Flags();
    flags.setFlags(flagMap);
    flags.setAnalyticsProcessor(analyticsProcessor);
    flags.setDefaultFlagHandler(defaultFlagHandler);

    return flags;
  }

  /**
   * Return the flags instance.
   *
   * @param apiFlags Dictionary with api flags
   * @param analyticsProcessor instance of analytics processor
   * @param defaultFlagHandler handler for default flags if present
   */
  public static Flags fromApiFlags(
      List<FeatureStateModel> apiFlags,
      AnalyticsProcessor analyticsProcessor,
      FlagsmithFlagDefaults defaultFlagHandler) {

    Map<String, BaseFlag> flagMap = new HashMap<>();

    for (FeatureStateModel flag : apiFlags) {
      flagMap.put(
          flag.getFeature().getName(),
          Flag.fromFeatureStateModel(flag));
    }

    Flags flags = new Flags();
    flags.setFlags(flagMap);
    flags.setAnalyticsProcessor(analyticsProcessor);
    flags.setDefaultFlagHandler(defaultFlagHandler);

    return flags;
  }

  /**
   * Build flags object from evaluation result.
   *
   * @param evaluationResult evaluation result
   * @param analyticsProcessor instance of analytics processor
   * @param defaultFlagHandler handler for default flags if present
   */
  public static Flags fromEvaluationResult(
      EvaluationResult evaluationResult,
      AnalyticsProcessor analyticsProcessor,
      DefaultFlagHandler defaultFlagHandler) {
    Map<String, BaseFlag> flagMap = evaluationResult.getFlags().stream()
        .collect(
            Collectors.toMap(
                (fs) -> fs.getName(),
                (fs) -> {
                  Flag flag = new Flag();
                  flag.setFeatureName(fs.getName());
                  flag.setValue(fs.getValue());
                  flag.setEnabled(fs.getEnabled());
                  return flag;
                }));

    Flags flags = new Flags();
    flags.setFlags(flagMap);
    flags.setAnalyticsProcessor(analyticsProcessor);
    flags.setDefaultFlagHandler(defaultFlagHandler);

    return flags;
  }

  /**
   * returns the list of all flags.
   */
  public List<BaseFlag> getAllFlags() {
    return flags.values().stream().collect(Collectors.toList());
  }

  /**
   * is feature enabled, null if not present.
   *
   * @param featureName Feature name
   */
  public boolean isFeatureEnabled(String featureName)
      throws FlagsmithClientError {
    return this.getFlag(featureName).getEnabled();
  }

  /**
   * Get the feature value, null if not present.
   *
   * @param featureName Feature name
   */
  public Object getFeatureValue(String featureName) throws FlagsmithClientError {
    return getFlag(featureName).getValue();
  }

  /**
   * Get the feature, null if not present.
   *
   * @param featureName feature name
   */
  public BaseFlag getFlag(String featureName) throws FlagsmithClientError {
    if (!flags.containsKey(featureName)) {
      if (defaultFlagHandler != null) {
        return defaultFlagHandler.evaluateDefaultFlag(featureName);
      }
      throw new FeatureNotFoundError("Feature does not exist: " + featureName);
    }

    BaseFlag flag = flags.get(featureName);

    if (analyticsProcessor != null && flag instanceof Flag) {
      Flag flagObj = (Flag) flag;
      if (flagObj.getFeatureId() != null) {
        analyticsProcessor.trackFeature(flagObj.getFeatureName());
      }
    }

    return flag;
  }
}
