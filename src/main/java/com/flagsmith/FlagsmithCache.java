package com.flagsmith;

import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Here are the fields we expose from the cache to outside this library.
 * Do not expose the cache here directly, i.e. getCache().
 */
public interface FlagsmithCache {
  void cleanUp();
  void invalidateAll();
  void invalidate(String userId);
  long estimatedSize();
  CacheStats stats();
}
