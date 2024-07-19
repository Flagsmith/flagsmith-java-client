package com.flagsmith.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.interfaces.FlagsmithSdk;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import lombok.Getter;
import lombok.ToString;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Getter
public class AnalyticsProcessor {

  private final String analyticsEndpoint = "analytics/flags/";
  private Integer analyticsTimer = 10;
  private Map<String, LongAdder> analyticsData;
  @ToString.Exclude private FlagsmithSdk api;
  private Long nextFlush;
  private AtomicBoolean isFlushing = new AtomicBoolean(false);
  private RequestProcessor requestProcessor;
  private HttpUrl analyticsUrl;
  FlagsmithLogger logger;

  /**
   * instantiate with HTTP client.
   * @param client client instance
   */
  public AnalyticsProcessor(OkHttpClient client) {
    this(null, client, null);
  }

  /**
   * instantiate with api and client.
   * @param api api instance
   * @param client client instance
   */
  public AnalyticsProcessor(FlagsmithSdk api, OkHttpClient client) {
    this(api, client, new FlagsmithLogger());
  }

  /**
   * Instantiate with API wrapper, logger and HTTP client.
   * @param api Api instance
   * @param client client instance
   * @param logger logger instance
   */
  public AnalyticsProcessor(FlagsmithSdk api, OkHttpClient client, FlagsmithLogger logger) {
    this(api, logger, new RequestProcessor(client, logger));
  }

  /**
   * Instantiate with API wrapper, logger, HTTP client and timeout.
   * @param api API object
   * @param logger Logger instance
   * @param requestProcessor request processor instance
   */
  public AnalyticsProcessor(
      FlagsmithSdk api, FlagsmithLogger logger, RequestProcessor requestProcessor) {
    this.analyticsData = new ConcurrentHashMap<String, LongAdder>();
    this.requestProcessor = requestProcessor;
    this.logger = logger;
    this.nextFlush = Instant.now().getEpochSecond() + analyticsTimer;
    this.api = api;
  }

  /**
   * The requestor is private, by default uses FlagsmithSDK requestor.
   *
   * @return
   */
  private RequestProcessor getRequestProcessor() {
    if (requestProcessor != null) {
      return requestProcessor;
    }

    return api.getRequestor();
  }

  /**
   * Set the logger object.
   *
   * @param logger logger instance
   */
  public void setLogger(FlagsmithLogger logger) {
    this.logger = logger;
  }


  public void setApi(FlagsmithSdk api) {
    this.api = api;
  }

  /**
   * Get the analytics url.
   */
  private HttpUrl getAnalyticsUrl() {
    if (api != null) {
      analyticsUrl = api.getConfig().getBaseUri().newBuilder(analyticsEndpoint).build();
    }
    return analyticsUrl;
  }

  /**
   * Push the analytics to the server.
   */
  public void flush() {
    // Make sure analytics data is only flushed once.
    if (isFlushing.compareAndSet(false, true)) {
      if (analyticsData.isEmpty()) {
        isFlushing.set(false);
        return;
      }

      String response;

      try {
        ObjectMapper mapper = MapperFactory.getMapper();
        response = mapper.writeValueAsString(analyticsData);
        analyticsData.clear();
      } catch (JsonProcessingException jpe) {
        logger.error("Error parsing analytics data to JSON.", jpe);
        isFlushing.set(false);
        return;
      }

      MediaType json = MediaType.parse("application/json; charset=utf-8");
      RequestBody body = RequestBody.create(json, response);

      Request request = api.newPostRequest(getAnalyticsUrl(), body);

      getRequestProcessor().executeAsync(request, Boolean.FALSE);

      setNextFlush();
      isFlushing.set(false);
    }
  }

  /**
   * Track the feature usage for analytics.
   * @param featureName name of the feature to track evaluation for
   */
  public void trackFeature(String featureName) {
    analyticsData.computeIfAbsent(featureName, k -> new LongAdder()).increment();

    if (nextFlush.compareTo(Instant.now().getEpochSecond()) < 0) {
      this.flush();
    }
  }

  private void setNextFlush() {
    nextFlush = Instant.now().getEpochSecond() + analyticsTimer;
  }

  public void close() {
    this.requestProcessor.close();
  }
}
