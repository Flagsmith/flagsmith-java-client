package com.flagsmith;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.flagsmith.config.FlagsmithConfig;
import okhttp3.mock.MockInterceptor;
import org.testng.annotations.Test;

public class FlagsmithConfigTest {

  @Test(groups = "unit")
  public void configTest_defaults() {
    final FlagsmithConfig flagsmithConfig = FlagsmithConfig.newBuilder().build();

    assertTrue(flagsmithConfig.httpClient.interceptors().isEmpty());
    assertEquals(5000, flagsmithConfig.httpClient.readTimeoutMillis());
    assertEquals(5000, flagsmithConfig.httpClient.writeTimeoutMillis());
    assertEquals(2000, flagsmithConfig.httpClient.connectTimeoutMillis());
  }

  @Test(groups = "unit")
  public void configTest_custom() {
    final FlagsmithConfig flagsmithConfig = FlagsmithConfig.newBuilder()
        .addHttpInterceptor(new MockInterceptor())
        .connectTimeout(1234)
        .readTimeout(3333)
        .writeTimeout(6666)
        .build();

    assertEquals(1, flagsmithConfig.httpClient.interceptors().size());
    assertEquals(3333, flagsmithConfig.httpClient.readTimeoutMillis());
    assertEquals(6666, flagsmithConfig.httpClient.writeTimeoutMillis());
    assertEquals(1234, flagsmithConfig.httpClient.connectTimeoutMillis());
  }

  @Test(groups = "unit")
  public void configTest_multipleInterceptors() {
    final FlagsmithConfig flagsmithConfig = FlagsmithConfig.newBuilder()
        .addHttpInterceptor(new MockInterceptor())
        .addHttpInterceptor(new MockInterceptor())
        .build();

    assertEquals(2, flagsmithConfig.httpClient.interceptors().size());
  }
}
