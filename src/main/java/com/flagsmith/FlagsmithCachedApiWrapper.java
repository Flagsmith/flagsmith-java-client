package com.flagsmith;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.interfaces.FlagsmithSdk;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class FlagsmithCachedApiWrapper implements FlagsmithSdk {

  private final FlagsmithApiWrapper flagsmithApiWrapper;
  private final FlagsmithCacheConfig.FlagsmithInternalCache cache;

  public FlagsmithCachedApiWrapper(final FlagsmithCacheConfig.FlagsmithInternalCache cache,
      final FlagsmithApiWrapper flagsmithApiWrapper) {
    this.cache = cache;
    this.flagsmithApiWrapper = flagsmithApiWrapper;
  }

  @Override
  public List<FeatureStateModel> getFeatureFlags(String identifier, boolean doThrow) {
    String cacheKey = cache.getEnvFlagsCacheKey();
    if (identifier == null && StringUtils.isBlank(cacheKey)) {
      // caching environment flags disabled
      return flagsmithApiWrapper.getFeatureFlags(null, doThrow);
    }
    FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    if (identifier != null) {
      assertValidUser(identifier);
      flagsAndTraits =  cache.getCache()
          .get(identifier, k -> getCacheObjectFromFlags(
                  flagsmithApiWrapper.getFeatureFlags(identifier, doThrow)
              ));
    } else {
      flagsAndTraits = cache.getCache()
          .get(cacheKey, k -> getCacheObjectFromFlags(
                  flagsmithApiWrapper.getFeatureFlags(null, doThrow)
              ));
    }

    return flagsAndTraits.getFlags();
  }

  @Override
  public List<FeatureStateModel> getFeatureFlags(boolean doThrow) {
    return flagsmithApiWrapper.getFeatureFlags(doThrow);
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(String identifier, boolean doThrow) {
    assertValidUser(identifier);
    return cache.getCache()
        .get(identifier, k -> flagsmithApiWrapper.getUserFlagsAndTraits(identifier, doThrow));
  }

  @Override
  public TraitRequest postUserTraits(String identifier, TraitModel toUpdate, boolean doThrow) {
    assertValidUser(identifier);
    final FlagsAndTraits flagsAndTraits = getCachedFlagsIfTraitsMatch(identifier,
        Arrays.asList(toUpdate));
    if (flagsAndTraits != null) {
      flagsmithApiWrapper.getLogger()
          .info("User trait unchanged for user {}, trait: {}", identifier, toUpdate);
      return new TraitRequest(toUpdate);
    }
    return flagsmithApiWrapper.postUserTraits(identifier, toUpdate, doThrow);
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(
      String identifier, List<TraitModel> traits, boolean doThrow) {
    assertValidUser(identifier);
    FlagsAndTraits flagsAndTraits = getCachedFlagsIfTraitsMatch(identifier, traits);
    if (flagsAndTraits != null) {
      return flagsAndTraits;
    }
    flagsAndTraits = flagsmithApiWrapper.identifyUserWithTraits(identifier, traits, doThrow);
    cache.getCache().put(identifier, flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public EnvironmentModel getEnvironment() {
    return flagsmithApiWrapper.getEnvironment();
  }

  @Override
  public FlagsmithConfig getConfig() {
    return this.flagsmithApiWrapper.getConfig();
  }

  @Override
  public FlagsmithCache getCache() {
    return cache;
  }

  private FlagsAndTraits getCachedFlagsIfTraitsMatch(
      String identifier, List<TraitModel> traitsToMatch
  ) {
    final FlagsAndTraits flagsAndTraits = cache.getCache().getIfPresent(identifier);
    if (flagsAndTraits == null) {
      // cache doesnt exist for this user
      return null;
    } else if (flagsAndTraits.getTraits() != null) {

      final boolean allTraitsFound =
          traitsToMatch == null
              || traitsToMatch.isEmpty()
              || traitsToMatch.stream()
              .allMatch(t -> {
                final TraitRequest newTrait = new TraitRequest(
                    null, t.getTraitKey(), t.getTraitValue()
                );
                return flagsAndTraits.getTraits().contains(newTrait);
              });

      // if all traits already have the same value, then there is no need to get new flags
      if (allTraitsFound) {
        return flagsAndTraits;
      }
    }

    // cache exists but does not match the target trait, lets invalidate this cache entry
    cache.getCache().invalidate(identifier);
    return null;
  }

  private FlagsAndTraits getCacheObjectFromFlags(List<FeatureStateModel> featureStates) {
    return FlagsAndTraits.builder().flags(featureStates).build();
  }

  private FlagsAndTraits getCacheObjectFromTraits(List<TraitModel> traits) {
    return FlagsAndTraits.builder().traits(traits).build();
  }
}
