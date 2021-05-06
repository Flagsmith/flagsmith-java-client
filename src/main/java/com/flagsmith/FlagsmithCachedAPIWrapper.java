package com.flagsmith;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
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
    String cacheKey = cache.getProjectFlagsCacheKey();
    if (user == null && StringUtils.isBlank(cacheKey)) {
      // caching project flags disabled
      return flagsmithAPIWrapper.getFeatureFlags(null, doThrow);
    }
    if (user != null) {
      assertValidUser(user);
      cacheKey = user.getIdentifier();
    }
    return cache.getCache().get(cacheKey, k -> flagsmithAPIWrapper.getFeatureFlags(user, doThrow));
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
    assertValidUser(user);
    return cache.getCache().get(user.getIdentifier(), k -> flagsmithAPIWrapper.getUserFlagsAndTraits(user, doThrow));
  }

  @Override
  public Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow) {
    assertValidUser(user);
    final FlagsAndTraits flagsAndTraits = getCachedFlagsIfTraitsMatch(user, Arrays.asList(toUpdate));
    if (flagsAndTraits != null) {
      flagsmithAPIWrapper.getLogger().info("User trait unchanged for user {}, trait: {}", user.getIdentifier(), toUpdate);
      return toUpdate;
    }
    return flagsmithAPIWrapper.postUserTraits(user, toUpdate, doThrow);
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow) {
    assertValidUser(user);
    FlagsAndTraits flagsAndTraits = getCachedFlagsIfTraitsMatch(user, traits);
    if (flagsAndTraits != null) {
      return flagsAndTraits;
    }
    flagsAndTraits = flagsmithAPIWrapper.identifyUserWithTraits(user, traits, doThrow);
    cache.getCache().put(user.getIdentifier(), flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public FlagsmithCache getCache() {
    return cache;
  }

  private FlagsAndTraits getCachedFlagsIfTraitsMatch(FeatureUser user, List<Trait> traitsToMatch) {
    final FlagsAndTraits flagsAndTraits = cache.getCache().getIfPresent(user.getIdentifier());
    if (flagsAndTraits == null) {
      // cache doesnt exist for this user
      return null;
    } else if (flagsAndTraits.getTraits() != null) {

      final boolean allTraitsFound =
          traitsToMatch == null ||
          traitsToMatch.isEmpty() ||
          traitsToMatch.stream().allMatch(t -> {
            final Trait newTrait = new Trait(null, t.getKey(), t.getValue());
            return flagsAndTraits.getTraits().contains(newTrait);
      });

      // if all traits already have the same value, then there is no need to get new flags
      if (allTraitsFound) {
        return flagsAndTraits;
      }
    }

    // cache exists but does not match the target trait, lets invalidate this cache entry
    cache.getCache().invalidate(user.getIdentifier());
    return null;
  }
}
