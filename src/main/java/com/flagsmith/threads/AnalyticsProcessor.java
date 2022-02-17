package com.flagsmith.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithApiWrapper;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class AnalyticsProcessor {

  private final String analyticsEndpoint = "analytics/flags/";
  private final Integer analyticsTimer = 10;
  private Map<Integer, Integer> analyticsData;
  private FlagsmithApiWrapper api;
  private Long nextFlush;
  private RequestProcessor requestProcessor;
  private HttpUrl analyticsUrl;
  FlagsmithLogger logger;


  /**
   * instantiate with api and client.
   * @param api api instance
   * @param client client instance
   */
  public AnalyticsProcessor(FlagsmithApiWrapper api, OkHttpClient client) {
    this(api, client, new FlagsmithLogger());
  }

  /**
   * Instantiate with API wrapper, logger and HTTP client.
   * @param api Api instance
   * @param client client instance
   * @param logger logger instance
   */
  public AnalyticsProcessor(FlagsmithApiWrapper api, OkHttpClient client, FlagsmithLogger logger) {
    this(api, logger, new RequestProcessor(client, logger));
  }

  /**
   * Instantiate with API wrapper, logger, HTTP client and timeout.
   * @param api API object
   * @param logger Logger instance
   * @param requestProcessor request processor instance
   */
  public AnalyticsProcessor(
      FlagsmithApiWrapper api, FlagsmithLogger logger, RequestProcessor requestProcessor) {
    this.analyticsData = new HashMap<Integer, Integer>();
    this.api = api;
    this.requestProcessor = requestProcessor;
    this.logger = logger;
    analyticsUrl = api.getConfig().getBaseUri().newBuilder(analyticsEndpoint).build();
  }

  /**
   * Push the analytics to the server.
   */
  public void flush() {

    if (analyticsData.isEmpty()) {
      return;
    }

    String response;

    try {
      ObjectMapper mapper = MapperFactory.getMappper();
      response = mapper.writeValueAsString(analyticsData);
    } catch (JsonProcessingException jpe) {
      logger.error("Error parsing analytics data to JSON.", jpe);
      return;
    }

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(response, json);

    Request request = api.newPostRequest(analyticsUrl, body);

    requestProcessor.executeAsync(request, new TypeReference<Object>() {}, Boolean.FALSE);

    analyticsData.clear();
    setNextFlush();
  }

  /**
   * Track the feature usage for analytics.
   * @param featureId feature id
   */
  public void trackFeature(Integer featureId) {
    analyticsData.put(featureId, analyticsData.getOrDefault(featureId, 0) + 1);

    if (nextFlush.compareTo(Instant.now().getEpochSecond()) > 0) {
      this.flush();
    }
  }

  private void setNextFlush() {
    nextFlush = Instant.now().getEpochSecond() + analyticsTimer;
  }


}
