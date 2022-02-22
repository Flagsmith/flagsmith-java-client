package com.flagsmith;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.mockito.ArgumentMatchers.anyString;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;


import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.Flags;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import okhttp3.mock.MockInterceptor;
import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FlagsmithClientHttpErrorsTest {

  private static final String API_KEY = "bad-key";
  private static final HashMap<String, String> customHeaders = new HashMap() {{
    put("x-custom-header", "value1");
    put("x-my-key", "value2");
  }};
  FlagsmithClient flagsmithClient;
  private MockInterceptor interceptor;

  @BeforeMethod(groups = "integration-offline")
  public void init() {
    interceptor = new MockInterceptor();

    FlagsmithConfig defaultConfig = FlagsmithConfig.newBuilder()
        .addHttpInterceptor(interceptor).retries(new Retry(1))
        .build();

    flagsmithClient = FlagsmithClient.newBuilder()
        .setApiKey(API_KEY)
        .withApiUrl("http://bad-url/")
        .withCustomHttpHeaders(customHeaders)
        .enableLogging(FlagsmithLoggerLevel.INFO)
        .withConfiguration(defaultConfig)
        .build();
  }

  @Test(groups = "integration-offline", enabled = false)
  public void testClient_When_Get_Features_Then_Empty() throws FlagsmithApiError {
    /*interceptor.addRule()
        .get("http://bad-url/flags/")
        .respond("[]", MEDIATYPE_JSON);*/

    Flags featureFlags = flagsmithClient.getEnvironmentFlags();

    assertNotNull(featureFlags, "Should feature flags back");
    assertTrue(featureFlags.getFlags().isEmpty(), "Should not have test featureFlags back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Cache_Disabled_Return_Null() {
    FlagsmithCache cache = flagsmithClient.getCache();

    Assert.assertNull(cache);
  }
}
