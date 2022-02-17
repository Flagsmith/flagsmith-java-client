package com.flagsmith.interfaces;

import com.flagsmith.FeatureUser;
import com.flagsmith.FlagsAndTraits;
import com.flagsmith.Trait;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.interfaces.FlagsmithCache;
import java.util.List;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

public interface FlagsmithSdk {

  // API Endpoints
  FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow);

  List<FeatureStateModel> getFeatureFlags(boolean doThrow);

  FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow);

  Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow);

  FlagsAndTraits identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow);

  FlagsmithConfig getConfig();
  
  EnvironmentModel getEnvironment();

  // Cache
  default FlagsmithCache getCache() {
    return null;
  }

  /**
   * validate user has a valid identifier.
   * @param user user object
   */
  default void assertValidUser(@NonNull FeatureUser user) {
    if (StringUtils.isBlank(user.getIdentifier())) {
      throw new IllegalArgumentException("Missing user identifier");
    }
  }
}
