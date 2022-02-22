package com.flagsmith.interfaces;

import com.flagsmith.FlagsAndTraits;
import com.flagsmith.TraitRequest;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import java.util.List;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

public interface FlagsmithSdk {

  // API Endpoints
  List<FeatureStateModel> getFeatureFlags(String identifier, boolean doThrow);

  List<FeatureStateModel> getFeatureFlags(boolean doThrow);

  FlagsAndTraits getUserFlagsAndTraits(String identifier, boolean doThrow);

  TraitRequest postUserTraits(String identifier, TraitModel toUpdate, boolean doThrow);

  FlagsAndTraits identifyUserWithTraits(
      String identifier, List<TraitModel> traits, boolean doThrow
  );

  FlagsmithConfig getConfig();
  
  EnvironmentModel getEnvironment();

  // Cache
  default FlagsmithCache getCache() {
    return null;
  }

  /**
   * validate user has a valid identifier.
   * @param identifier user identifier
   */
  default void assertValidUser(@NonNull String identifier) {
    if (StringUtils.isBlank(identifier)) {
      throw new IllegalArgumentException("Missing user identifier");
    }
  }
}
