package com.flagsmith;

import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Here are the fields we expose from the cache to outside this library. Do not expose the cache
 * here directly, i.e. getCache().
 */
public interface FlagsmithCache {

  /**
   * Performs any pending maintenance operations needed by the cache.
   */
  void cleanUp();

  /**
   * Discards all entries in the cache.
   */
  void invalidateAll();

  /**
   * Discards any cached value for key userId.
   *
   * @param userId
   */
  void invalidate(String userId);

  /**
   * Returns the approximate number of entries in this cache. The value returned is an estimate; the
   * actual count may differ if there are concurrent insertions or removals, or if some entries are
   * pending removal due to expiration or weak/soft reference collection. In the case of stale
   * entries this inaccuracy can be mitigated by performing a cleanUp() first.
   *
   * @return the estimated size
   */
  long estimatedSize();

  /**
   * Returns a current snapshot of this cache's cumulative statistics. All statistics are
   * initialized to zero, and are monotonically increasing over the lifetime of the cache.
   *
   * @return stats object
   */
  CacheStats stats();

  /**
   * Returns the value associated with key in this cache, or null if there is no cached value for
   * key. It will not attempt to fetch flags from Flagsmith.
   *
   * @param key a key to retrieve value for
   * @return flags and traits in cache or null
   */
  FlagsAndTraits getIfPresent(String key);

  /**
   * Returns the environment level flags/traits cache key.
   * <p>Flags for users are stored in the cache using the user-identifier as the cache key.
   * <p>For environment level flags, you need to configure a key with the builder to enable caching
   * environment flags.
   * <p>This method returns the key you
   * configured with the builder.
   *
   * @return string
   */
  String getEnvFlagsCacheKey();
}
