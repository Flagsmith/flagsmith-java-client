package com.flagsmith;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

class FlagsmithCachedApiWrapper implements FlagsmithSdk {

  private final FlagsmithApiWrapper flagsmithApiWrapper;
  private final FlagsmithCacheConfig.FlagsmithInternalCache cache;

  public FlagsmithCachedApiWrapper(final FlagsmithCacheConfig.FlagsmithInternalCache cache,
      final FlagsmithApiWrapper flagsmithApiWrapper) {
    this.cache = cache;
    this.flagsmithApiWrapper = flagsmithApiWrapper;
  }

  @Override
  public FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow) {
    String cacheKey = cache.getEnvFlagsCacheKey();
    if (user == null && StringUtils.isBlank(cacheKey)) {
      // caching environment flags disabled
      return flagsmithApiWrapper.getFeatureFlags(null, doThrow);
    }
    if (user != null) {
      assertValidUser(user);
      return cache.getCache()
          .get(user.getIdentifier(), k -> flagsmithApiWrapper.getFeatureFlags(user, doThrow));
    }
    return cache.getCache().get(cacheKey, k -> flagsmithApiWrapper.getFeatureFlags(user, doThrow));
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
    assertValidUser(user);
    return cache.getCache()
        .get(user.getIdentifier(), k -> flagsmithApiWrapper.getUserFlagsAndTraits(user, doThrow));
  }

  @Override
  public Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow) {
    assertValidUser(user);
    final FlagsAndTraits flagsAndTraits = getCachedFlagsIfTraitsMatch(user,
        Arrays.asList(toUpdate));
    if (flagsAndTraits != null) {
      flagsmithApiWrapper.getLogger()
          .info("User trait unchanged for user {}, trait: {}", user.getIdentifier(), toUpdate);
      return toUpdate;
    }
    return flagsmithApiWrapper.postUserTraits(user, toUpdate, doThrow);
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(
      FeatureUser user, List<Trait> traits, boolean doThrow) {
    assertValidUser(user);
    FlagsAndTraits flagsAndTraits = getCachedFlagsIfTraitsMatch(user, traits);
    if (flagsAndTraits != null) {
      return flagsAndTraits;
    }
    flagsAndTraits = flagsmithApiWrapper.identifyUserWithTraits(user, traits, doThrow);
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
          traitsToMatch == null
              || traitsToMatch.isEmpty()
              || traitsToMatch.stream()
              .allMatch(t -> {
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
