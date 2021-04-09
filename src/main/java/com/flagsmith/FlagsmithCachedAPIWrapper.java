package com.flagsmith;

import java.util.List;

class FlagsmithCachedAPIWrapper implements FlagsmithSDK {
  private final FlagsmithAPIWrapper flagsmithAPIWrapper;
  private final FlagsmithCacheConfig.FlagsmithInternalCache cache;

  public FlagsmithCachedAPIWrapper(final FlagsmithCacheConfig.FlagsmithInternalCache cache, final FlagsmithAPIWrapper flagsmithAPIWrapper) {
    this.cache = cache;
    this.flagsmithAPIWrapper = flagsmithAPIWrapper;
  }

  @Override
  public FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow) {
    if (user == null) {
      // not caching project flags yet
      return flagsmithAPIWrapper.getFeatureFlags(null, doThrow);
    }
    assertValidUser(user);
    return cache.getCache().get(user.getIdentifier(), k -> flagsmithAPIWrapper.getFeatureFlags(user, doThrow));
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
    assertValidUser(user);
    return cache.getCache().get(user.getIdentifier(), k -> flagsmithAPIWrapper.getUserFlagsAndTraits(user, doThrow));
  }

  @Override
  public Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow) {
    assertValidUser(user);
    final FlagsAndTraits flagsAndTraits = cache.getCache().getIfPresent(user.getIdentifier());
    final Trait newTrait = new Trait(null, toUpdate.getKey(), toUpdate.getValue());
    // if the trait already has the same value, then there is no need to update it
    if (flagsAndTraits != null &&
        flagsAndTraits.getTraits() != null &&
        flagsAndTraits.getTraits().contains(newTrait)) {
      flagsmithAPIWrapper.getLogger().info("User trait unchanged for user {}, trait: {}", user.getIdentifier(), toUpdate);
      return toUpdate;
    }
    // cache exists but does not match the target trait, lets invalidate this cache entry
    cache.getCache().invalidate(user.getIdentifier());
    return flagsmithAPIWrapper.postUserTraits(user, toUpdate, doThrow);
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow) {
    assertValidUser(user);
    FlagsAndTraits flagsAndTraits = flagsmithAPIWrapper.identifyUserWithTraits(user, traits, doThrow);
    cache.getCache().put(user.getIdentifier(), flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public FlagsmithCache getCache() {
    return cache;
  }
}
