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
public class FlagsmithCachedEndpointsTest {

  private FlagsmithCachedEndpoints sut;
  private FlagsmithCacheConfig.FlagsmithInternalCache flagsmithInternalCache;
  private FlagsmithEndpoints flagsmithEndpoints;
  private FlagsmithLogger flagsmithLogger;
  private Cache<String, FlagsAndTraits> cache;

  @BeforeMethod(groups = "unit")
  public void init() {
    flagsmithInternalCache = mock(FlagsmithCacheConfig.FlagsmithInternalCache.class);
    flagsmithEndpoints = mock(FlagsmithEndpoints.class);
    sut = new FlagsmithCachedEndpoints(flagsmithInternalCache, flagsmithEndpoints);

    cache = Caffeine.newBuilder().maximumSize(2).build();
    when(flagsmithInternalCache.getCache()).thenReturn(cache);

    flagsmithLogger = mock(FlagsmithLogger.class);
    when(flagsmithEndpoints.getLogger()).thenReturn(flagsmithLogger);
  }

  @Test(groups = "unit")
  public void getFeatureFlags_noCachingProjectFlags() {
    // Arrange
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithEndpoints.getFeatureFlags(null, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).getFeatureFlags(eq(null), eq(true));
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(flagsAndTraits, actualFeatureFlags);
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_nullIdentifier() {
    // Act
    final FeatureUser user = new FeatureUser();
    assertThrows(IllegalArgumentException.class, () -> sut.getFeatureFlags(user, true));

    // Assert
    verify(flagsmithEndpoints, times(0)).getFeatureFlags(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithEndpoints.getFeatureFlags(user, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(user, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).getFeatureFlags(eq(user), eq(true));
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
    verify(flagsmithEndpoints, times(0)).getFeatureFlags(any(), anyBoolean());
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
    verify(flagsmithEndpoints, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.getUserFlagsAndTraits(new FeatureUser(), true));

    // Assert
    verify(flagsmithEndpoints, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithEndpoints.getUserFlagsAndTraits(user, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.getUserFlagsAndTraits(user, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).getUserFlagsAndTraits(eq(user), eq(true));
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
    verify(flagsmithEndpoints, times(0)).getUserFlagsAndTraits(any(), anyBoolean());
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
    verify(flagsmithEndpoints, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithEndpoints, times(0)).getLogger();
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.postUserTraits(new FeatureUser(), new Trait(), true));

    // Assert
    verify(flagsmithEndpoints, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithEndpoints, times(0)).getLogger();
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void postUserTraits_updateBecauseTraitValueNotInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final Trait oldTrait = new Trait();
    oldTrait.setKey("old-key");
    oldTrait.setValue("old-val");

    final Trait newTrait = new Trait();
    newTrait.setKey("old-key");
    newTrait.setValue("new-val");

    when(flagsmithEndpoints.postUserTraits(user, oldTrait, true)).thenReturn(newTrait);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, oldTrait, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).postUserTraits(eq(user), eq(oldTrait), eq(true));
    verify(flagsmithEndpoints, times(0)).getLogger();
    verify(flagsmithInternalCache, times(2)).getCache();
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

    when(flagsmithEndpoints.postUserTraits(user, oldTrait, true)).thenReturn(newTrait);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, oldTrait, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).postUserTraits(eq(user), eq(oldTrait), eq(true));
    verify(flagsmithEndpoints, times(0)).getLogger();
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

    when(flagsmithEndpoints.postUserTraits(user, oldTrait, true)).thenReturn(newTrait);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, oldTrait, true);

    // Assert
    verify(flagsmithEndpoints, times(0)).postUserTraits(any(), any(), anyBoolean());
    verify(flagsmithEndpoints, times(1)).getLogger();
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
    verify(flagsmithEndpoints, times(0)).identifyUserWithTraits(any(), any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class, () -> sut.identifyUserWithTraits(new FeatureUser(), new ArrayList<>(), true));

    // Assert
    verify(flagsmithEndpoints, times(0)).identifyUserWithTraits(any(), any(), anyBoolean());
    verify(flagsmithInternalCache, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final ArrayList<Trait> traits = new ArrayList<>();

    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    when(flagsmithEndpoints.identifyUserWithTraits(user, traits, true)).thenReturn(flagsAndTraits);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, traits, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).identifyUserWithTraits(eq(user), eq(traits), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(flagsAndTraits, cache.getIfPresent(user.getIdentifier()));
    assertEquals(flagsAndTraits, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithEvenIfInCache() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("test-user");

    final ArrayList<Trait> traits = new ArrayList<>();

    final FlagsAndTraits oldFlags = new FlagsAndTraits();
    oldFlags.setTraits(traits);
    cache.put(user.getIdentifier(), oldFlags);

    final FlagsAndTraits newFlags = new FlagsAndTraits();
    when(flagsmithEndpoints.identifyUserWithTraits(user, traits, true)).thenReturn(newFlags);

    // Act
    final FlagsAndTraits actualUserFlagsAndTraits = sut.identifyUserWithTraits(user, traits, true);

    // Assert
    verify(flagsmithEndpoints, times(1)).identifyUserWithTraits(any(), eq(traits), eq(true));
    verify(flagsmithInternalCache, times(1)).getCache();
    assertEquals(newFlags, cache.getIfPresent(user.getIdentifier()));
    assertEquals(newFlags, actualUserFlagsAndTraits);
    assertEquals(1, cache.estimatedSize());
  }
}
