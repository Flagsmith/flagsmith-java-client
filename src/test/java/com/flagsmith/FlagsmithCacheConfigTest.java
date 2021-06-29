package com.flagsmith;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.benmanes.caffeine.cache.Policy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class FlagsmithCacheConfigTest {

  private static final int DEFAULT_MAX_SIZE = 10;
  private static final Duration DEFAULT_EXPIRE_AFTER_WRITE = Duration.ofMinutes(5);

  @Test(groups = "unit")
  public void testNewBuilder_defaults() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig.newBuilder().build();
    final Policy<String, FlagsAndTraits> cachePolicy = flagsmithCacheConfig.cache.getCache()
        .policy();

    assertNotNull(cachePolicy);
    assertFalse(cachePolicy.isRecordingStats());
    assertFalse(cachePolicy.expireAfterAccess().isPresent());
    assertFalse(cachePolicy.refreshAfterWrite().isPresent());
    assertFalse(cachePolicy.expireVariably().isPresent());
    assertNull(flagsmithCacheConfig.cache.getEnvFlagsCacheKey());

    verifyDefaultAfterWrite(cachePolicy);
    verifyDefaultMaxSize(cachePolicy);
  }

  private void verifyDefaultAfterWrite(Policy<String, FlagsAndTraits> cachePolicy) {
    assertTrue(cachePolicy.expireAfterWrite().isPresent());
    assertEquals(DEFAULT_EXPIRE_AFTER_WRITE,
        cachePolicy.expireAfterWrite().get().getExpiresAfter());
  }

  @Test(groups = "unit")
  public void testNewBuilder_expireAfterWrite() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build();
    final Policy<String, FlagsAndTraits> cachePolicy = flagsmithCacheConfig.cache.getCache()
        .policy();

    assertNotNull(cachePolicy);
    assertFalse(cachePolicy.isRecordingStats());
    assertFalse(cachePolicy.expireAfterAccess().isPresent());
    assertFalse(cachePolicy.refreshAfterWrite().isPresent());
    assertFalse(cachePolicy.expireVariably().isPresent());
    assertNull(flagsmithCacheConfig.cache.getEnvFlagsCacheKey());

    assertTrue(cachePolicy.expireAfterWrite().isPresent());
    assertEquals(Duration.ofSeconds(30), cachePolicy.expireAfterWrite().get().getExpiresAfter());
    verifyDefaultMaxSize(cachePolicy);
  }

  @Test(groups = "unit")
  public void testNewBuilder_expireAfterAccess() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .expireAfterAccess(20, TimeUnit.HOURS)
        .build();
    final Policy<String, FlagsAndTraits> cachePolicy = flagsmithCacheConfig.cache.getCache()
        .policy();

    assertNotNull(cachePolicy);
    assertFalse(cachePolicy.isRecordingStats());
    assertFalse(cachePolicy.refreshAfterWrite().isPresent());
    assertFalse(cachePolicy.expireVariably().isPresent());
    assertNull(flagsmithCacheConfig.cache.getEnvFlagsCacheKey());

    assertTrue(cachePolicy.expireAfterAccess().isPresent());
    assertEquals(Duration.ofHours(20), cachePolicy.expireAfterAccess().get().getExpiresAfter());
    verifyDefaultAfterWrite(cachePolicy);
    verifyDefaultMaxSize(cachePolicy);
  }

  @Test(groups = "unit")
  public void testNewBuilder_maxSize() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .maxSize(210)
        .build();
    final Policy<String, FlagsAndTraits> cachePolicy = flagsmithCacheConfig.cache.getCache()
        .policy();

    assertNotNull(cachePolicy);
    assertFalse(cachePolicy.isRecordingStats());
    assertFalse(cachePolicy.refreshAfterWrite().isPresent());
    assertFalse(cachePolicy.expireVariably().isPresent());
    assertFalse(cachePolicy.expireAfterAccess().isPresent());
    assertNull(flagsmithCacheConfig.cache.getEnvFlagsCacheKey());

    assertTrue(cachePolicy.eviction().isPresent());
    assertEquals(210, cachePolicy.eviction().get().getMaximum());
    assertFalse(cachePolicy.eviction().get().isWeighted());
    verifyDefaultAfterWrite(cachePolicy);
  }

  @Test(groups = "unit")
  public void testNewBuilder_recordStats() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .recordStats()
        .build();
    final Policy<String, FlagsAndTraits> cachePolicy = flagsmithCacheConfig.cache.getCache()
        .policy();

    assertNotNull(cachePolicy);
    assertFalse(cachePolicy.refreshAfterWrite().isPresent());
    assertFalse(cachePolicy.expireVariably().isPresent());
    assertFalse(cachePolicy.expireAfterAccess().isPresent());
    assertNull(flagsmithCacheConfig.cache.getEnvFlagsCacheKey());

    assertTrue(cachePolicy.isRecordingStats());

    assertTrue(cachePolicy.expireAfterWrite().isPresent());
    assertEquals(DEFAULT_EXPIRE_AFTER_WRITE,
        cachePolicy.expireAfterWrite().get().getExpiresAfter());

    verifyDefaultMaxSize(cachePolicy);
  }

  @Test(groups = "unit")
  public void testNewBuilder_combined() {
    final String envFlagsCacheKey = "some-random-key";
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .maxSize(250)
        .recordStats()
        .enableEnvLevelCaching(envFlagsCacheKey)
        .build();
    final Policy<String, FlagsAndTraits> cachePolicy = flagsmithCacheConfig.cache.getCache()
        .policy();

    assertNotNull(cachePolicy);
    assertFalse(cachePolicy.refreshAfterWrite().isPresent());
    assertFalse(cachePolicy.expireVariably().isPresent());
    assertFalse(cachePolicy.expireAfterAccess().isPresent());

    assertTrue(cachePolicy.eviction().isPresent());
    assertEquals(250, cachePolicy.eviction().get().getMaximum());
    assertFalse(cachePolicy.eviction().get().isWeighted());

    assertEquals(envFlagsCacheKey, flagsmithCacheConfig.cache.getEnvFlagsCacheKey());

    assertTrue(cachePolicy.isRecordingStats());
    verifyDefaultAfterWrite(cachePolicy);
  }

  @Test(groups = "unit")
  public void testNewBuilder_enableEnvLevelCaching() {
    final String envFlagsCacheKey = "some-random-key";

    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig.newBuilder()
        .enableEnvLevelCaching(envFlagsCacheKey)
        .build();

    assertEquals(envFlagsCacheKey, flagsmithCacheConfig.cache.getEnvFlagsCacheKey());
  }

  @Test(groups = "unit", expectedExceptions = IllegalArgumentException.class)
  public void testNewBuilder_enableEnvLevelCaching_null() {
    FlagsmithCacheConfig.newBuilder()
        .enableEnvLevelCaching(null)
        .build();
  }

  @Test(groups = "unit", expectedExceptions = IllegalArgumentException.class)
  public void testNewBuilder_enableEnvLevelCaching_emptyString() {
    FlagsmithCacheConfig.newBuilder()
        .enableEnvLevelCaching("")
        .build();
  }

  private void verifyDefaultMaxSize(Policy<String, FlagsAndTraits> cachePolicy) {
    assertTrue(cachePolicy.eviction().isPresent());
    assertEquals(DEFAULT_MAX_SIZE, cachePolicy.eviction().get().getMaximum());
    assertFalse(cachePolicy.eviction().get().isWeighted());
  }
}
