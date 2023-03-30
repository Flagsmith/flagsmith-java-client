package com.flagsmith.config;

import com.flagsmith.models.Flags;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.platform.commons.annotation.Testable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;


public class FlagsmithCacheConfigTest {

  private static final int DEFAULT_MAX_SIZE = 10;
  private static final Duration DEFAULT_EXPIRE_AFTER_WRITE = Duration.ofMinutes(5);

  @Test
  public void testNewBuilder_defaults() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig.newBuilder().build();
    final Policy<String, Flags> cachePolicy = flagsmithCacheConfig.cache.getCache()
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

  private void verifyDefaultAfterWrite(Policy<String, Flags> cachePolicy) {
    assertTrue(cachePolicy.expireAfterWrite().isPresent());
    assertEquals(DEFAULT_EXPIRE_AFTER_WRITE,
        cachePolicy.expireAfterWrite().get().getExpiresAfter());
  }

  @Test
  public void testNewBuilder_expireAfterWrite() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build();
    final Policy<String, Flags> cachePolicy = flagsmithCacheConfig.cache.getCache()
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

  @Test
  public void testNewBuilder_expireAfterAccess() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .expireAfterAccess(20, TimeUnit.HOURS)
        .build();
    final Policy<String, Flags> cachePolicy = flagsmithCacheConfig.cache.getCache()
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

  @Test
  public void testNewBuilder_maxSize() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .maxSize(210)
        .build();
    final Policy<String, Flags> cachePolicy = flagsmithCacheConfig.cache.getCache()
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

  @Test
  public void testNewBuilder_recordStats() {
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .recordStats()
        .build();
    final Policy<String, Flags> cachePolicy = flagsmithCacheConfig.cache.getCache()
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

  @Test
  public void testNewBuilder_combined() {
    final String envFlagsCacheKey = "some-random-key";
    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig
        .newBuilder()
        .maxSize(250)
        .recordStats()
        .enableEnvLevelCaching(envFlagsCacheKey)
        .build();
    final Policy<String, Flags> cachePolicy = flagsmithCacheConfig.cache.getCache()
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

  @Test
  public void testNewBuilder_enableEnvLevelCaching() {
    final String envFlagsCacheKey = "some-random-key";

    final FlagsmithCacheConfig flagsmithCacheConfig = FlagsmithCacheConfig.newBuilder()
        .enableEnvLevelCaching(envFlagsCacheKey)
        .build();

    assertEquals(envFlagsCacheKey, flagsmithCacheConfig.cache.getEnvFlagsCacheKey());
  }

  @Test
  public void testNewBuilder_enableEnvLevelCaching_null() {
    assertThrows(IllegalArgumentException.class, () -> FlagsmithCacheConfig.newBuilder()
        .enableEnvLevelCaching(null)
        .build());
  }

  @Test
  public void testNewBuilder_enableEnvLevelCaching_emptyString() {
    assertThrows(IllegalArgumentException.class, () -> FlagsmithCacheConfig.newBuilder()
        .enableEnvLevelCaching("")
        .build());
  }

  private void verifyDefaultMaxSize(Policy<String, Flags> cachePolicy) {
    assertTrue(cachePolicy.eviction().isPresent());
    assertEquals(DEFAULT_MAX_SIZE, cachePolicy.eviction().get().getMaximum());
    assertFalse(cachePolicy.eviction().get().isWeighted());
  }
}
