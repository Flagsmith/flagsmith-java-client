package com.flagsmith;

import java.util.List;

class FlagsmithCachedEndpoints implements FlagsmithSDK {
  private final FlagsmithEndpoints flagsmithEndpoints;
  private final FlagsmithCacheConfig.FlagsmithInternalCache cache;

  public FlagsmithCachedEndpoints(final FlagsmithCacheConfig.FlagsmithInternalCache cache, final FlagsmithEndpoints flagsmithEndpoints) {
    this.cache = cache;
    this.flagsmithEndpoints = flagsmithEndpoints;
  }

  @Override
  public FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow) {
    if (user == null) {
      // not caching project flags yet
      return flagsmithEndpoints.getFeatureFlags(user, doThrow);
    }
    return cache.getCache().get(user.getIdentifier(), k -> flagsmithEndpoints.getFeatureFlags(user, doThrow));
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
    return cache.getCache().get(user.getIdentifier(), k -> flagsmithEndpoints.getUserFlagsAndTraits(user, doThrow));
  }

  @Override
  public Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow) {
    final FlagsAndTraits flagsAndTraits = cache.getCache().getIfPresent(user.getIdentifier());
    final Trait newTrait = new Trait(null, toUpdate.getKey(), toUpdate.getValue());
    // if the trait already has the same value, then there is no need to update it
    if (flagsAndTraits != null &&
        flagsAndTraits.getTraits() != null &&
        flagsAndTraits.getTraits().contains(newTrait)) {
      flagsmithEndpoints.logger.info("User trait unchanged for user {}, trait: {}", user.getIdentifier(), toUpdate);
      return toUpdate;
    }
    // cache exists but does not match the target trait, lets invalidate this cache entry
    cache.getCache().invalidate(user.getIdentifier());
    return flagsmithEndpoints.postUserTraits(user, toUpdate, doThrow);
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow) {
    FlagsAndTraits flagsAndTraits = flagsmithEndpoints.identifyUserWithTraits(user, traits, doThrow);
    cache.getCache().put(user.getIdentifier(), flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public FlagsmithCache getCache() {
    return cache;
  }
}
