package com.flagsmith;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

interface FlagsmithSDK {
  // API Endpoints
  FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow);
  FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow);
  Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow);
  FlagsAndTraits identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow);

  // Cache
  default FlagsmithCache getCache() {
    return null;
  }

  // Validation
  default void assertValidUser(@NonNull FeatureUser user) {
    if (StringUtils.isBlank(user.getIdentifier())) {
      throw new IllegalArgumentException("Missing user identifier");
    }
  }
}
