package com.flagsmith.models;

import com.flagsmith.AnalyticsProcessor;
import com.flagsmith.flagengine.features.FeatureStateModel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class Flags {
  private Map<String, BaseFlag> flags;
  private AnalyticsProcessor analyticsProcessor;

  /**
   *
   * @param featureStates
   * @param analyticsProcessor
   * @param identityId
   * @param defaults
   * @return
   */
  public static Flags fromFeatureStateModels(
      List<FeatureStateModel> featureStates,
      AnalyticsProcessor analyticsProcessor,
      Object identityId, List<DefaultFlag> defaults) {

    Map<String, BaseFlag> flagMap = featureStates.stream()
        .collect(
            Collectors.toMap(
                (fs) -> fs.getFeature().getName(),
                (fs) -> Flag.fromFeatureStateModel(fs, identityId)
            ));

    if (defaults != null && !defaults.isEmpty()) {
      for (DefaultFlag defaultFlag: defaults) {
        if (flagMap.containsKey(defaultFlag.getFeatureName())) {
          flagMap.put(defaultFlag.getFeatureName(), defaultFlag);
        }
      }
    }

    Flags flags = new Flags();
    flags.setFlags(flagMap);
    flags.setAnalyticsProcessor(analyticsProcessor);

    return flags;
  }
}
