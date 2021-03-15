package com.flagsmith;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.util.concurrent.TimeUnit;

public final class FlagsmithCacheConfig {

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

    if (builder.maxWeight > -1) {
      caffeineBuilder = caffeineBuilder
          .maximumWeight(builder.maxWeight);
    }

    if (builder.recordStats) {
      caffeineBuilder = caffeineBuilder.recordStats();
    }
    this.cache = new FlagsmithInternalCache(caffeineBuilder.build());
  }

  public static FlagsmithCacheConfig.Builder newBuilder() {
    return new FlagsmithCacheConfig.Builder();
  }

  class FlagsmithInternalCache implements FlagsmithCache {
    private final Cache<String, FlagsAndTraits> cache;

    public FlagsmithInternalCache(Cache<String, FlagsAndTraits> cache) {
      this.cache = cache;
    }

    public void cleanUp() {
      cache.cleanUp();
    }

    public void invalidateAll() {
      cache.invalidateAll();
    }

    public void invalidate(String userId) {
      cache.invalidate(userId);
    }

    public long estimatedSize() {
      return cache.estimatedSize();
    }

    public CacheStats stats() {
      return cache.stats();
    }

    public Cache<String, FlagsAndTraits> getCache() {
      return cache;
    }
  }

  public static class Builder {
    private TimeUnit expireAfterWriteTimeUnit;
    private int expireAfterWrite = -1;
    private TimeUnit expireAfterAccessTimeUnit;
    private int expireAfterAccess = -1;
    private int maxSize = -1;
    private int maxWeight = -1;
    private boolean recordStats = false;

    private Builder() {
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration
     * has elapsed after the entry's creation, or the most recent replacement of its value.
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
     * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed
     * after the entry's creation, the most recent replacement of its value, or its last read.
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
     * Specifies the maximum number of entries the cache may contain. Note that the cache may evict an entry before
     * this limit is exceeded or temporarily exceed the threshold while evicting.
     * As the cache size grows close to the maximum, the cache evicts entries that are less likely to be used again.
     * For example, the cache may evict an entry because it hasn't been used recently or very often.
     *
     * @param maxSize size. When size is zero, elements will be evicted immediately after being loaded into the cache.
     *                This can be useful in testing, or to disable caching temporarily without a code change.
     * @return the Builder
     */
    public Builder maxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    /**
     * Specifies the maximum weight of entries the cache may contain.
     *
     * @param maxWeight weight. When size is zero, elements will be evicted immediately after being loaded into the cache.
     *                This can be useful in testing, or to disable caching temporarily without a code change.
     * @return the Builder
     */
    public Builder maxWeight(int maxWeight) {
      this.maxWeight = maxWeight;
      return this;
    }

    /**
     * Enables the accumulation of CacheStats during the operation of the cache.
     * Without this Cache.stats() will return zero for all statistics. Note that recording statistics requires
     * bookkeeping to be performed with each operation, and thus imposes a performance penalty on cache operation.
     *
     * @return the Builder
     */
    public Builder recordStats() {
      this.recordStats = true;
      return this;
    }

    public FlagsmithCacheConfig build() {
      return new FlagsmithCacheConfig(this);
    }
  }
}
