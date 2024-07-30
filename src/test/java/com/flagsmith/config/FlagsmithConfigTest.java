package com.flagsmith.config;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagsmith.config.FlagsmithConfig.Protocol;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import okhttp3.mock.MockInterceptor;
import org.junit.jupiter.api.Test;

public class FlagsmithConfigTest {

  @Test
  public void configTest_defaults() {
    final FlagsmithConfig flagsmithConfig = FlagsmithConfig.newBuilder().build();

    assertTrue(flagsmithConfig.getHttpClient().interceptors().isEmpty());
    assertEquals(5000, flagsmithConfig.getHttpClient().readTimeoutMillis());
    assertEquals(5000, flagsmithConfig.getHttpClient().writeTimeoutMillis());
    assertEquals(2000, flagsmithConfig.getHttpClient().connectTimeoutMillis());
    assertNull(flagsmithConfig.getHttpClient().proxy());
  }

  @Test
  public void configTest_custom() {
    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy", 1234));

    final FlagsmithConfig flagsmithConfig = FlagsmithConfig.newBuilder()
        .addHttpInterceptor(new MockInterceptor())
        .connectTimeout(1234)
        .readTimeout(3333)
        .writeTimeout(6666)
        .withProxy(proxy)
        .build();

    assertEquals(1, flagsmithConfig.getHttpClient().interceptors().size());
    assertEquals(3333, flagsmithConfig.getHttpClient().readTimeoutMillis());
    assertEquals(6666, flagsmithConfig.getHttpClient().writeTimeoutMillis());
    assertEquals(1234, flagsmithConfig.getHttpClient().connectTimeoutMillis());
    assertEquals(proxy, flagsmithConfig.getHttpClient().proxy());
  }

  @Test
  public void configTest_multipleInterceptors() {
    final FlagsmithConfig flagsmithConfig = FlagsmithConfig.newBuilder()
        .addHttpInterceptor(new MockInterceptor())
        .addHttpInterceptor(new MockInterceptor())
        .build();

    assertEquals(2, flagsmithConfig.getHttpClient().interceptors().size());
  }

  @Test
  public void configTest_supportedProtocols() {
    final FlagsmithConfig defaultFlagsmithConfig = FlagsmithConfig.newBuilder().build();

    assertEquals(2, defaultFlagsmithConfig.getHttpClient().protocols().size());

    final FlagsmithConfig customFlagsmithConfig = FlagsmithConfig.newBuilder().withSupportedProtocols(
        Collections.singletonList(Protocol.HTTP_1_1)).build();

    assertEquals(1, customFlagsmithConfig.getHttpClient().protocols().size());
    assertEquals(okhttp3.Protocol.HTTP_1_1, customFlagsmithConfig.getHttpClient().protocols().get(0));
  }
}
