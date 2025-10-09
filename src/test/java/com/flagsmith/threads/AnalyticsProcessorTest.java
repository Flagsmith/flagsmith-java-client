package com.flagsmith.threads;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithApiWrapper;
import com.flagsmith.FlagsmithException;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import java.io.IOException;

import lombok.SneakyThrows;
import okhttp3.Response;
import okhttp3.mock.MockInterceptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AnalyticsProcessorTest {

  private FlagsmithApiWrapper api;
  private FlagsmithLogger flagsmithLogger;
  private FlagsmithConfig defaultConfig;
  private MockInterceptor interceptor;
  private RequestProcessor requestProcessor;
  private AnalyticsProcessor analytics;

  @BeforeEach
  public void init() {
    flagsmithLogger = mock(FlagsmithLogger.class);
    doThrow(new FlagsmithException("error Response")).when(flagsmithLogger)
        .httpError(any(), any(Response.class), eq(true));
    doThrow(new FlagsmithException("error IOException")).when(flagsmithLogger)
        .httpError(any(), any(IOException.class), eq(true));

    interceptor = new MockInterceptor();
    defaultConfig = FlagsmithConfig.newBuilder().addHttpInterceptor(interceptor)
        .retries(new Retry(1)).baseUri("http://base-uri/")
        .build();
    api = mock(FlagsmithApiWrapper.class);
    requestProcessor = mock(RequestProcessor.class);
    when(api.getConfig()).thenReturn(defaultConfig);
    analytics = new AnalyticsProcessor(api, flagsmithLogger, requestProcessor);
  }

  @Test
  public void AnalyticsProcessor_checkAnalyticsData() {
    String featureName = "foo";

    analytics.trackFeature(featureName);
    Assertions.assertTrue(analytics.getAnalyticsData().containsKey(featureName));
    Assertions.assertEquals(analytics.getAnalyticsData().size(), 1);

    analytics.trackFeature(featureName);
    Assertions.assertEquals(analytics.getAnalyticsData().size(), 1);
    Assertions.assertEquals(analytics.getAnalyticsData().get(featureName).intValue(), 2);
  }

  @Test
  public void AnalyticsProcessor_checkAnalyticsDataCheckFlushRuns() throws InterruptedException {
    String featureName = "foo";
    Long nextFlush = analytics.getNextFlush();
    analytics.trackFeature(featureName);
    Assertions.assertEquals(nextFlush, analytics.getNextFlush());
    Thread.sleep(11000);
    analytics.trackFeature(featureName);
    Assertions.assertEquals(analytics.getAnalyticsData().size(), 0);
    Assertions.assertNotEquals(nextFlush, analytics.getNextFlush());
    verify(api, times(1)).newPostRequest(any(), any());
    verify(requestProcessor, times(1)).executeAsync(any(), any());
  }

  @Test
  public void AnalyticsProcessor_checkAnalyticsRequestCheckFlushRuns() {
    String featureName = "foo";

    Long nextFlush = analytics.getNextFlush();
    analytics.trackFeature(featureName);
    Assertions.assertEquals(nextFlush, analytics.getNextFlush());

    analytics.flush();
    verify(api, times(1)).newPostRequest(any(), any());
    verify(requestProcessor, times(1)).executeAsync(any(), any());
  }

  @Test
  public void testClose_ShutsDownExecutor() {
    // When
    analytics.close();

    // Then
    verify(requestProcessor, times(1)).close();
  }

  @Test
  @SneakyThrows
  public void AnalyticsProcessor_longAdderGetsSerializedCorrectly() {
    analytics.trackFeature("foo");

    ObjectMapper mapper = MapperFactory.getMapper();
    String response = mapper.writeValueAsString(analytics.getAnalyticsData());

    Assertions.assertEquals("{\"foo\":1}", response);
  }
}
