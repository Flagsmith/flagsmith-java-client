package com.flagsmith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class FlagsmithCachedApiWrapperTest {

  private FlagsmithCachedApiWrapper sut;
  private FlagsmithCacheConfig.FlagsmithInternalCache flagsmithInternalCache;
  private FlagsmithApiWrapper flagsmithAPIWrapper;
  private FlagsmithLogger flagsmithLogger;
  private Cache<String, FlagsAndTraits> cache;

  @BeforeMethod(groups = "unit")
  public void init() {
    flagsmithInternalCache = mock(FlagsmithCacheConfig.FlagsmithInternalCache.class);
    flagsmithAPIWrapper = mock(FlagsmithApiWrapper.class);
    sut = new FlagsmithCachedApiWrapper(flagsmithInternalCache, flagsmithAPIWrapper);

    cache = Caffeine.newBuilder().maximumSize(2).build();
    when(flagsmithInternalCache.getCache()).thenReturn(cache);

    flagsmithLogger = mock(FlagsmithLogger.class);
    when(flagsmithAPIWrapper.getLogger()).thenReturn(flagsmithLogger);
  }

  @Test(groups = "unit")
  public void getCache_returnsInternalCache() {
    final FlagsmithCache actualInternalCache = sut.getCache();
    assertEquals(flagsmithInternalCache, actualInternalCache);
  }

  @Test(groups = "unit")
  public void getFeatureFlags_envFlags_cacheEnabled_dontFetchFlagsWhenInCache() {
    // Arrange
    final String cacheKey = "cacheKey";
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithInternalCache.getEnvFlagsCacheKey()).thenReturn(cacheKey);
    cache.put(cacheKey, flagsAndTraits);

    // Act
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getFeatureFlags(any(), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits.getFlags(), actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_nullIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.getFeatureFlags("", true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getFeatureFlags(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange

    final List<FeatureStateModel> flagsAndTraits = new ArrayList<>();
    when(flagsmithAPIWrapper.getFeatureFlags("test-user", true)).thenReturn(flagsAndTraits);

    // Act
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags("test-user", true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).getFeatureFlags(eq("test-user"), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent("test-user"));
    assertEquals(flagsAndTraits, actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_fetchFlagsFromCacheAndNotFromFlagsmith() {
    // Arrange

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    cache.put("test-user", flagsAndTraits);

    // Act
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags("test-user", true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getFeatureFlags(any(), anyBoolean());
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits.getFlags(), cache.getIfPresent("test-user"));
    assertEquals(flagsAndTraits.getFlags(), actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_nullUser() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.getUserFlagsAndTraits(null, true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class,
        () -> sut.getUserFlagsAndTraits("", true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.getUserFlagsAndTraits("test-user", true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.getUserFlagsAndTraits("test-user", true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).getUserFlagsAndTraits(eq("test-user"), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent("test-user"));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_fetchFlagsFromCacheAndNotFromFlagsmith() {
    // Arrange

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    cache.put("test-user", flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.getUserFlagsAndTraits("test-user", true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent("test-user"));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_nullUser() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.postUserTraits(null, new TraitRequest(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class,
        () -> sut.postUserTraits("", new TraitRequest(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_updateBecauseFlagsNotCached() {
    // Arrange

    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final TraitRequest newTrait = new TraitRequest();
    newTrait.setKey("old-key");
    newTrait.setValue("new-val");

    when(flagsmithAPIWrapper.postUserTraits("test-user", oldTrait, true)).thenReturn(newTrait);

    // Act
    final TraitModel actualTrait = sut.postUserTraits("test-user", oldTrait, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).postUserTraits(eq("test-user"), eq(oldTrait), eq(true));
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(1)).getCache();
    assertNull(cache.getIfPresent("test-user"));
    assertEquals(0, cache.estimatedSize());
    assertEquals(newTrait, actualTrait);
  }

  @Test(groups = "unit")
  public void postUserTraits_updateBecauseTraitValueHasChangedFromCache() {
    // Arrange
    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final TraitRequest newTrait = new TraitRequest();
    newTrait.setKey("old-key");
    newTrait.setValue("new-val");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    cache.put("test-user", flagsAndTraits);
    assertEquals(1, cache.estimatedSize());

    when(flagsmithAPIWrapper.postUserTraits("test-user", oldTrait, true)).thenReturn(newTrait);

    // Act
    final TraitModel actualTrait = sut.postUserTraits("test-user", oldTrait, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).postUserTraits(eq("test-user"), eq(oldTrait), eq(true));
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(2)).getCache();
    assertNull(cache.getIfPresent("test-user"));
    assertEquals(0, cache.estimatedSize());
    assertEquals(newTrait, actualTrait);
  }

  @Test(groups = "unit")
  public void postUserTraits_doNotUpdateBecauseTraitValueHasNotChangedFromCache() {
    // Arrange
    final TraitRequest newTrait = new TraitRequest();

    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    flagsAndTraits.setTraits(new ArrayList<>());
    flagsAndTraits.getTraits().add(oldTrait);
    cache.put("test-user", flagsAndTraits);
    assertEquals(1, cache.estimatedSize());

    when(flagsmithAPIWrapper.postUserTraits("test-user", oldTrait, true)).thenReturn(newTrait);

    // Act
    final TraitModel actualTrait = sut.postUserTraits("test-user", oldTrait, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithAPIWrapper, times(1)).getLogger();
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent("test-user"));
    assertEquals(1, cache.estimatedSize());
    assertEquals(oldTrait, actualTrait);
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_nullUser() {
    // Act
    assertThrows(IllegalArgumentException.class,
        () -> sut.identifyUserWithTraits(null, new ArrayList<>(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class,
        () -> sut.identifyUserWithTraits("", new ArrayList<>(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0))
        .identifyUserWithTraits(any(), any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithAndStoreThemInCache_whenCacheEmpty() {
    // Arrange
    final ArrayList<TraitModel> traits = new ArrayList<>();

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.identifyUserWithTraits("test-user", traits, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits("test-user", traits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(eq("test-user"), eq(traits), eq(true));
    verify(flagsmithInternalCache, times(2)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent("test-user"));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_dontFetchFlagsFromFlagsmithIfInCacheAndNewTraitsAreEmpty() {
    // Arrange
    final ArrayList<TraitModel> traits = new ArrayList<>();

    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(traits);
    cache.put("test-user", oldFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits("test-user", traits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), eq(traits), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(1, cache.estimatedSize());
    assertEquals(oldFlags, actualUserFlagsAndTraits);
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithEvenIfInCache_withSameTraitKey() {
    // Arrange
    final String traitKey = "trait-key";

    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey(traitKey);
    oldTrait.setValue("old-val");
    final ArrayList<TraitModel> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put("test-user", oldFlags);

    final TraitRequest newTrait = new TraitRequest();
    newTrait.setKey(traitKey);
    newTrait.setValue("new-val");
    final ArrayList<TraitModel> newTraits = new ArrayList<>();
    newTraits.add(newTrait);
    final FlagsAndTraits newFlags = new FlagsAndTraits();
    newFlags.setTraits(newTraits);
    when(flagsmithAPIWrapper.identifyUserWithTraits("test-user", newTraits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut
        .identifyUserWithTraits("test-user", newTraits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(any(), eq(newTraits), eq(true));
    verify(flagsmithInternalCache, times(3)).getCache();
    assertEquals(newFlags, cache.getIfPresent("test-user"));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithEvenIfInCache_withNewTraitKey() {
    // Arrange
    final String traitValue = "value";

    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue(traitValue);
    final ArrayList<TraitModel> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put("test-user", oldFlags);

    final TraitRequest newTrait = new TraitRequest();
    newTrait.setKey("trait-key-2");
    newTrait.setValue(traitValue);
    final ArrayList<TraitModel> newTraits = new ArrayList<>();
    newTraits.add(newTrait);
    final FlagsAndTraits newFlags = new FlagsAndTraits();
    newFlags.setTraits(newTraits);
    when(flagsmithAPIWrapper.identifyUserWithTraits("test-user", newTraits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut
        .identifyUserWithTraits("test-user", newTraits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(any(), eq(newTraits), eq(true));
    verify(flagsmithInternalCache, times(3)).getCache();
    assertEquals(newFlags, cache.getIfPresent("test-user"));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithEvenIfInCache_withExtraTrait() {
    // Arrange
    final String traitValue = "value";
    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue(traitValue);
    final ArrayList<TraitModel> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put("test-user", oldFlags);

    final TraitRequest newTrait = new TraitRequest();
    newTrait.setKey("trait-key-2");
    newTrait.setValue(traitValue);
    final ArrayList<TraitModel> newTraits = new ArrayList<>();
    newTraits.add(newTrait);
    newTraits.add(oldTrait);
    final FlagsAndTraits newFlags = new FlagsAndTraits();
    newFlags.setTraits(newTraits);
    when(flagsmithAPIWrapper.identifyUserWithTraits("test-user", newTraits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut
        .identifyUserWithTraits("test-user", newTraits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(any(), eq(newTraits), eq(true));
    verify(flagsmithInternalCache, times(3)).getCache();
    assertEquals(newFlags, cache.getIfPresent("test-user"));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_useCachedValue_whenEmptyTraitList() {
    // Arrange

    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue("value");
    final ArrayList<TraitModel> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put("test-user", oldFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut
        .identifyUserWithTraits("test-user", new ArrayList<>(), true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(oldFlags, cache.getIfPresent("test-user"));
    assertEquals(oldFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_useCachedValue_whenNullTraitList() {
    // Arrange
    final TraitRequest oldTrait = new TraitRequest();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue("value");
    final ArrayList<TraitModel> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put("test-user", oldFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits("test-user", null, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(oldFlags, cache.getIfPresent("trait-user"));
    assertEquals(oldFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }
}
