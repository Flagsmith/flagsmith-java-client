package com.flagsmith;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.interfaces.FlagsmithCache;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class FlagsmithCachedClientTest {

  @Test(groups = "unit")
  public void testClient_When_Cache_Enabled_Return_Cache_Obj() {
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .setApiKey("api-key")
        .withCache(FlagsmithCacheConfig
            .newBuilder()
            .enableEnvLevelCaching("newkey-random-name")
            .maxSize(2)
            .build())
        .build();

    FlagsmithCache cache = client.getCache();

    Assert.assertNotNull(cache);
  }
}
