package com.flagsmith;

import java.util.List;

interface FlagsmithSDK {
  FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow);
  FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow);
  Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow);
  FlagsAndTraits identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow);
  default FlagsmithCache getCache() {
    return null;
  }
}
