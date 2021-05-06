package com.flagsmith;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;

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

@Test(groups = "unit")
public class FlagsmithCachedAPIWrapperTest {

  private FlagsmithCachedAPIWrapper sut;
  private FlagsmithCacheConfig.FlagsmithInternalCache flagsmithInternalCache;
  private FlagsmithAPIWrapper flagsmithAPIWrapper;
  private FlagsmithLogger flagsmithLogger;
  private Cache<String, FlagsAndTraits> cache;

  @BeforeMethod(groups = "unit")
  public void init() {
    flagsmithInternalCache = mock(FlagsmithCacheConfig.FlagsmithInternalCache.class);
    flagsmithAPIWrapper = mock(FlagsmithAPIWrapper.class);
    sut = new FlagsmithCachedAPIWrapper(flagsmithInternalCache, flagsmithAPIWrapper);

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
  public void getFeatureFlags_projectFlags_cacheDisabled() {
    // Arrange
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.getFeatureFlags(null, true)).thenReturn(flagsAndTraits);
    when(flagsmithInternalCache.getProjectFlagsCacheKey()).thenReturn(null);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).getFeatureFlags(eq(null), eq(true));
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(flagsAndTraits, actualFeatureFlags);
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_projectFlags_cacheEnabled_fetchFlagsWhenNotInCache() {
    // Arrange
    final String cacheKey = "cacheKey";
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.getFeatureFlags(null, true)).thenReturn(flagsAndTraits);
    when(flagsmithInternalCache.getProjectFlagsCacheKey()).thenReturn(cacheKey);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).getFeatureFlags(eq(null), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_projectFlags_cacheEnabled_dontFetchFlagsWhenInCache() {
    // Arrange
    final String cacheKey = "cacheKey";
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithInternalCache.getProjectFlagsCacheKey()).thenReturn(cacheKey);
    cache.put(cacheKey, flagsAndTraits);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getFeatureFlags(any(), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_nullIdentifier() {
    // Act
    final FeatureUser user = new FeatureUser();
    assertThrows(IllegalArgumentException.class, () -> sut.getFeatureFlags(user, true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getFeatureFlags(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.getFeatureFlags(user, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(user, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).getFeatureFlags(eq(user), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(flagsAndTraits, actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_fetchFlagsFromCacheAndNotFromFlagsmith() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    cache.put(user.getIdentifier(), flagsAndTraits);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(user, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getFeatureFlags(any(), anyBoolean());
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(flagsAndTraits, actualFeatureFlags);
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
    assertThrows(IllegalArgumentException.class, () -> sut.getUserFlagsAndTraits(new FeatureUser(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.getUserFlagsAndTraits(user, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.getUserFlagsAndTraits(user, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).getUserFlagsAndTraits(eq(user), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_fetchFlagsFromCacheAndNotFromFlagsmith() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    cache.put(user.getIdentifier(), flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.getUserFlagsAndTraits(user, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_nullUser() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.postUserTraits(null, new Trait(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.postUserTraits(new FeatureUser(), new Trait(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_updateBecauseFlagsNotCached() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final Trait newTrait = new Trait();
    newTrait.setKey("old-key");
    newTrait.setValue("new-val");

    when(flagsmithAPIWrapper.postUserTraits(user, oldTrait, true)).thenReturn(newTrait);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, oldTrait, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).postUserTraits(eq(user), eq(oldTrait), eq(true));
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(1)).getCache();
    assertNull(cache.getIfPresent(user.getIdentifier()));
    assertEquals(0, cache.estimatedSize());
    assertEquals(newTrait, actualTrait);
  }

  @Test(groups = "unit")
  public void postUserTraits_updateBecauseTraitValueHasChangedFromCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final Trait newTrait = new Trait();
    newTrait.setKey("old-key");
    newTrait.setValue("new-val");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    cache.put(user.getIdentifier(), flagsAndTraits);
    assertEquals(1, cache.estimatedSize());

    when(flagsmithAPIWrapper.postUserTraits(user, oldTrait, true)).thenReturn(newTrait);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, oldTrait, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).postUserTraits(eq(user), eq(oldTrait), eq(true));
    verify(flagsmithAPIWrapper, times(0)).getLogger();
    verify(flagsmithInternalCache, times(2)).getCache();
    assertNull(cache.getIfPresent(user.getIdentifier()));
    assertEquals(0, cache.estimatedSize());
    assertEquals(newTrait, actualTrait);
  }

  @Test(groups = "unit")
  public void postUserTraits_doNotUpdateBecauseTraitValueHasNotChangedFromCache() {
    // Arrange
    final Trait newTrait = new Trait();
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    flagsAndTraits.setTraits(new ArrayList<>());
    flagsAndTraits.getTraits().add(oldTrait);
    cache.put(user.getIdentifier(), flagsAndTraits);
    assertEquals(1, cache.estimatedSize());

    when(flagsmithAPIWrapper.postUserTraits(user, oldTrait, true)).thenReturn(newTrait);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, oldTrait, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithAPIWrapper, times(1)).getLogger();
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(1, cache.estimatedSize());
    assertEquals(oldTrait, actualTrait);
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_nullUser() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.identifyUserWithTraits(null, new ArrayList<>(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.identifyUserWithTraits(new FeatureUser(), new ArrayList<>(), true));

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithAndStoreThemInCache_whenCacheEmpty() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final ArrayList<Trait> traits = new ArrayList<>();

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithAPIWrapper.identifyUserWithTraits(user, traits, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, traits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(eq(user), eq(traits), eq(true));
    verify(flagsmithInternalCache, times(2)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_dontFetchFlagsFromFlagsmithIfInCacheAndNewTraitsAreEmpty() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final ArrayList<Trait> traits = new ArrayList<>();

    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(traits);
    cache.put(user.getIdentifier(), oldFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, traits, true);

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
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey(traitKey);
    oldTrait.setValue("old-val");
    final ArrayList<Trait> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put(user.getIdentifier(), oldFlags);

    final Trait newTrait = new Trait();
    newTrait.setKey(traitKey);
    newTrait.setValue("new-val");
    final ArrayList<Trait> newTraits = new ArrayList<>();
    newTraits.add(newTrait);
    final FlagsAndTraits newFlags = new FlagsAndTraits();
    newFlags.setTraits(newTraits);
    when(flagsmithAPIWrapper.identifyUserWithTraits(user, newTraits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, newTraits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(any(), eq(newTraits), eq(true));
    verify(flagsmithInternalCache, times(3)).getCache();
    assertEquals(newFlags, cache.getIfPresent(user.getIdentifier()));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithEvenIfInCache_withNewTraitKey() {
    // Arrange
    final String traitValue = "value";
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue(traitValue);
    final ArrayList<Trait> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put(user.getIdentifier(), oldFlags);

    final Trait newTrait = new Trait();
    newTrait.setKey("trait-key-2");
    newTrait.setValue(traitValue);
    final ArrayList<Trait> newTraits = new ArrayList<>();
    newTraits.add(newTrait);
    final FlagsAndTraits newFlags = new FlagsAndTraits();
    newFlags.setTraits(newTraits);
    when(flagsmithAPIWrapper.identifyUserWithTraits(user, newTraits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, newTraits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(any(), eq(newTraits), eq(true));
    verify(flagsmithInternalCache, times(3)).getCache();
    assertEquals(newFlags, cache.getIfPresent(user.getIdentifier()));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithEvenIfInCache_withExtraTrait() {
    // Arrange
    final String traitValue = "value";
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue(traitValue);
    final ArrayList<Trait> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put(user.getIdentifier(), oldFlags);

    final Trait newTrait = new Trait();
    newTrait.setKey("trait-key-2");
    newTrait.setValue(traitValue);
    final ArrayList<Trait> newTraits = new ArrayList<>();
    newTraits.add(newTrait);
    newTraits.add(oldTrait);
    final FlagsAndTraits newFlags = new FlagsAndTraits();
    newFlags.setTraits(newTraits);
    when(flagsmithAPIWrapper.identifyUserWithTraits(user, newTraits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, newTraits, true);

    // Assert
    verify(flagsmithAPIWrapper, times(1)).identifyUserWithTraits(any(), eq(newTraits), eq(true));
    verify(flagsmithInternalCache, times(3)).getCache();
    assertEquals(newFlags, cache.getIfPresent(user.getIdentifier()));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_useCachedValue_whenEmptyTraitList() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue("value");
    final ArrayList<Trait> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put(user.getIdentifier(), oldFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, new ArrayList<>(), true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(oldFlags, cache.getIfPresent(user.getIdentifier()));
    assertEquals(oldFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_useCachedValue_whenNullTraitList() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("trait-key-1");
    oldTrait.setValue("value");
    final ArrayList<Trait> oldTraits = new ArrayList<>();
    oldTraits.add(oldTrait);
    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(oldTraits);
    cache.put(user.getIdentifier(), oldFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, null, true);

    // Assert
    verify(flagsmithAPIWrapper, times(0)).identifyUserWithTraits(any(), any(), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(oldFlags, cache.getIfPresent(user.getIdentifier()));
    assertEquals(oldFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }
}
