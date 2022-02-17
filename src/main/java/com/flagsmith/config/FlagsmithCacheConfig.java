package com.flagsmith.config;

import com.flagsmith.FlagsAndTraits;
import com.flagsmith.interfaces.FlagsmithCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

@Data
public final class FlagsmithCacheConfig {

  private static final int DEFAULT_MAX_SIZE = 10;
  private static final int DEFAULT_EXPIRE_AFTER_WRITE = 5;
  private static final TimeUnit DEFAULT_EXPIRE_AFTER_WRITE_TIMEUNIT = TimeUnit.MINUTES;
  final FlagsmithInternalCache cache;

  private FlagsmithCacheConfig(Builder builder) {
    Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();

    if (builder.expireAfterWrite > -1) {
      caffeineBuilder = caffeineBuilder
          .expireAfterWrite(builder.expireAfterWrite, builder.expireAfterWriteTimeUnit);
    }

    if (builder.expireAfterAccess > -1) {
      caffeineBuilder = caffeineBuilder
          .expireAfterAccess(builder.expireAfterAccess, builder.expireAfterAccessTimeUnit);
    }

    if (builder.maxSize > -1) {
      caffeineBuilder = caffeineBuilder
          .maximumSize(builder.maxSize);
    }

    if (builder.recordStats) {
      caffeineBuilder = caffeineBuilder.recordStats();
    }

    this.cache = new FlagsmithInternalCache(caffeineBuilder.build(), builder.envFlagsCacheKey);
  }

  public static FlagsmithCacheConfig.Builder newBuilder() {
    return new FlagsmithCacheConfig.Builder();
  }

  public static class Builder {

    private TimeUnit expireAfterWriteTimeUnit = DEFAULT_EXPIRE_AFTER_WRITE_TIMEUNIT;
    private int expireAfterWrite = DEFAULT_EXPIRE_AFTER_WRITE;
    private TimeUnit expireAfterAccessTimeUnit;
    private int expireAfterAccess = -1;
    private int maxSize = DEFAULT_MAX_SIZE;
    private boolean recordStats = false;
    private String envFlagsCacheKey = null;

    private Builder() {
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed
     * duration has elapsed after the entry's creation, or the most recent replacement of its
     * value.
     *
     * @param duration an integer matching the time unit.
     * @param timeUnit minutes, seconds, etc.
     * @return the Builder
     */
    public Builder expireAfterWrite(int duration, TimeUnit timeUnit) {
      this.expireAfterWrite = duration;
      this.expireAfterWriteTimeUnit = timeUnit;
      return this;
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed
     * duration has elapsed after the entry's creation, the most recent replacement of its value, or
     * its last read.
     *
     * @param duration an integer matching the time unit.
     * @param timeUnit minutes, seconds, etc.
     * @return the Builder
     */
    public Builder expireAfterAccess(int duration, TimeUnit timeUnit) {
      this.expireAfterAccess = duration;
      this.expireAfterAccessTimeUnit = timeUnit;
      return this;
    }

    /**
     * Specifies the maximum number of entries the cache may contain. Note that the cache may evict
     * an entry before this limit is exceeded or temporarily exceed the threshold while evicting.
     *
     * <p>As the cache size grows close to the maximum, the cache evicts entries that are less
     * likely to be used again.
     *
     * <p>For example, the cache may evict an entry because it hasn't been used recently
     * or very often.
     *
     * @param maxSize size. When size is zero, elements will be evicted immediately after being
     *                loaded into the cache. This can be useful in testing, or to disable caching
     *                temporarily without a code change.
     * @return the Builder
     */
    public Builder maxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    /**
     * Enables the accumulation of CacheStats during the operation of the cache.
     *
     * <p>Without this Cache.stats() will return zero for all statistics. Note that recording
     * statistics requires bookkeeping to be performed with each operation, and thus imposes a
     * performance penalty on cache operation.
     *
     * @return the Builder
     */
    public Builder recordStats() {
      this.recordStats = true;
      return this;
    }

    /**
     * Enables caching for environment level flags.
     *
     * <p>Flags for users are stored in the cache using the user-identifier as the cache key.
     *
     * <p>For environment level flags, you need to configure a key with the builder to enable
     * caching environment flags.
     *
     * <p>This is required to ensure the programmer chooses an environment-level-key that does not
     * conflict with user identifiers.
     *
     * <p>IMPORTANT: make sure you set an environment key that will never match a user identifier.
     *
     * <p>Otherwise, the cache will not be able to distinguish between the 2.
     *
     * @param envFlagsCacheKey key to use in the cache for environment level flags
     * @return the Builder
     */
    public Builder enableEnvLevelCaching(@NonNull String envFlagsCacheKey) {
      if (StringUtils.isBlank(envFlagsCacheKey)) {
        throw new IllegalArgumentException("Missing environment level cache key");
      }
      this.envFlagsCacheKey = envFlagsCacheKey;
      return this;
    }

    public FlagsmithCacheConfig build() {
      return new FlagsmithCacheConfig(this);
    }
  }

  public class FlagsmithInternalCache implements FlagsmithCache {

    private final Cache<String, FlagsAndTraits> cache;
    private final String envFlagsCacheKey;

    public FlagsmithInternalCache(final Cache<String, FlagsAndTraits> cache,
        final String envFlagsCacheKey) {
      this.cache = cache;
      this.envFlagsCacheKey = envFlagsCacheKey;
    }

    public FlagsmithInternalCache(final Cache<String, FlagsAndTraits> cache) {
      this.cache = cache;
      this.envFlagsCacheKey = null;
    }

    @Override
    public void cleanUp() {
      cache.cleanUp();
    }

    @Override
    public void invalidateAll() {
      cache.invalidateAll();
    }

    @Override
    public void invalidate(String userId) {
      cache.invalidate(userId);
    }

    @Override
    public long estimatedSize() {
      return cache.estimatedSize();
    }

    @Override
    public CacheStats stats() {
      return cache.stats();
    }

    @Override
    public FlagsAndTraits getIfPresent(String key) {
      return cache.getIfPresent(key);
    }

    @Override
    public String getEnvFlagsCacheKey() {
      return this.envFlagsCacheKey;
    }

    // do not expose this method on the interface
    public Cache<String, FlagsAndTraits> getCache() {
      return cache;
    }
  }
}
