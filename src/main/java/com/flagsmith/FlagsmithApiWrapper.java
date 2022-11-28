package com.flagsmith;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.Flags;
import com.flagsmith.threads.AnalyticsProcessor;
import com.flagsmith.threads.RequestProcessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Data;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Data
public class FlagsmithApiWrapper implements FlagsmithSdk {

  private static final String AUTH_HEADER = "X-Environment-Key";
  private static final String ACCEPT_HEADER = "Accept";
  private static final Integer TIMEOUT = 15000;

  private final FlagsmithLogger logger;
  private final FlagsmithConfig defaultConfig;
  private final HashMap<String, String> customHeaders;
  // an api key per environment
  private final String apiKey;
  private RequestProcessor requestor;
  private FlagsmithCache cache = null;

  /**
   * Instantiate with cache.
   *
   * @param cache cache object
   * @param defaultConfig config object
   * @param customHeaders custom headers list
   * @param logger logger object
   * @param apiKey api key
   */
  public FlagsmithApiWrapper(
      final FlagsmithCache cache,
      final FlagsmithConfig defaultConfig,
      final HashMap<String, String> customHeaders,
      final FlagsmithLogger logger,
      final String apiKey
  ) {
    this(defaultConfig, customHeaders, logger, apiKey);
    this.cache = cache;
  }

  /**
   * Instantiate with config, custom headers, logger and apikey.
   * @param defaultConfig config object
   * @param customHeaders custom headers list
   * @param logger logger instance
   * @param apiKey api key
   */
  public FlagsmithApiWrapper(
      final FlagsmithConfig defaultConfig,
      final HashMap<String, String> customHeaders,
      final FlagsmithLogger logger,
      final String apiKey
  ) {
    this.defaultConfig = defaultConfig;
    this.customHeaders = customHeaders;
    this.logger = logger;
    this.apiKey = apiKey;
    requestor = new RequestProcessor(
        defaultConfig.getHttpClient(),
        logger,
        defaultConfig.getRetries()
    );
  }

  /**
   * Instantiate with config, custom headers, logger, apikey and request processor.
   * @param defaultConfig config object
   * @param customHeaders custom headers list
   * @param logger logger instance
   * @param apiKey api key
   * @param requestProcessor request processor
   */
  public FlagsmithApiWrapper(
          final FlagsmithConfig defaultConfig,
          final HashMap<String, String> customHeaders,
          final FlagsmithLogger logger,
          final String apiKey,
          final RequestProcessor requestProcessor
  ) {
    this.defaultConfig = defaultConfig;
    this.customHeaders = customHeaders;
    this.logger = logger;
    this.apiKey = apiKey;
    this.requestor = requestProcessor;
  }

  /**
   * Get Feature Flags from API.
   *
   * @param doThrow - whether throw exception or not
   * @return
   */
  public Flags getFeatureFlags(boolean doThrow) {
    Flags featureFlags = new Flags();

    if (getCache() != null && getCache().getEnvFlagsCacheKey() != null) {
      featureFlags = getCache().getIfPresent(getCache().getEnvFlagsCacheKey());

      if (featureFlags != null) {
        return featureFlags;
      }
    }

    HttpUrl urlBuilder = defaultConfig.getFlagsUri();
    Request request = this.newGetRequest(urlBuilder);

    Future<List<FeatureStateModel>> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<List<FeatureStateModel>>() {},
        doThrow
    );

    try {
      List<FeatureStateModel> featureFlagsResponse = featureFlagsFuture.get(
          TIMEOUT, TimeUnit.MILLISECONDS
      );

      if (featureFlagsResponse == null) {
        featureFlagsResponse = new ArrayList<>();
      }

      featureFlags = Flags.fromApiFlags(
          featureFlagsResponse,
          getConfig().getAnalyticsProcessor(),
          getConfig().getFlagsmithFlagDefaults()
      );

      if (getCache() != null && getCache().getEnvFlagsCacheKey() != null) {
        getCache().getCache().put(getCache().getEnvFlagsCacheKey(), featureFlags);
        logger.info("Got feature flags for flags = {} and cached.", featureFlags);
      }
    } catch (TimeoutException te) {
      logger.error("Timed out on fetching Feature flags.", te);
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
      if (doThrow) {
        throw new RuntimeException(ee);
      }
    }

    logger.info("Got feature flags for flags = {}", featureFlags);

    return featureFlags;
  }

  @Override
  public Flags identifyUserWithTraits(
      String identifier, List<TraitModel> traits, boolean doThrow
  ) {
    assertValidUser(identifier);
    Flags flagsAndTraits = null;

    if (getCache() != null && getCache().getEnvFlagsCacheKey() != null) {
      flagsAndTraits = getCache().getIfPresent(getCache().getEnvFlagsCacheKey());

      if (flagsAndTraits != null) {
        return flagsAndTraits;
      }
    }

    // we are using identities endpoint to create bulk user Trait
    HttpUrl url = defaultConfig.getIdentitiesUri();

    ObjectNode node = MapperFactory.getMapper().createObjectNode();
    node.put("identifier", identifier);

    if (traits != null) {
      node.putPOJO("traits", traits);
    }

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(json, node.toString());

    final Request request = this.newPostRequest(url, body);

    Future<FlagsAndTraitsResponse> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<FlagsAndTraitsResponse>() {},
        doThrow
    );

    try {
      FlagsAndTraitsResponse flagsAndTraitsResponse = featureFlagsFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);
      List<FeatureStateModel> flagsArray = flagsAndTraitsResponse != null
          && flagsAndTraitsResponse.flags != null
          ? flagsAndTraitsResponse.getFlags() : new ArrayList<>();

      flagsAndTraits = Flags.fromApiFlags(
          flagsArray,
          getConfig().getAnalyticsProcessor(),
          getConfig().getFlagsmithFlagDefaults()
      );

      if (getCache() != null) {
        getCache().getCache().put("identifier" + identifier, flagsAndTraits);
        logger.info("Got feature flags for flags = {} and cached.", flagsAndTraits);
      }

    } catch (TimeoutException ie) {
      logger.error("Timed out on fetching Feature flags.", ie);
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
      if (doThrow) {
        throw new RuntimeException(ee);
      }
    }

    logger.info("Got flags based on identify for identifier = {}, flags = {}",
        identifier, flagsAndTraits);

    return flagsAndTraits;
  }

  @Override
  public EnvironmentModel getEnvironment() {
    final Request request = newGetRequest(defaultConfig.getEnvironmentUri());

    Future<EnvironmentModel> environmentFuture = requestor.executeAsync(request,
        new TypeReference<EnvironmentModel>() {},
        Boolean.TRUE);

    try {
      return environmentFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ie) {
      logger.error("Timed out on fetching Feature flags.", ie);
    } catch (InterruptedException ie) {
      logger.error("Environment loading interrupted.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on Environment loading.", ee);
      throw new RuntimeException(ee);
    }

    return null;
  }

  @Override
  public FlagsmithConfig getConfig() {
    return this.defaultConfig;
  }

  @Override
  public FlagsmithCache getCache() {
    return cache;
  }

  public FlagsmithLogger getLogger() {
    return logger;
  }

  private Request.Builder newRequestBuilder() {
    final Request.Builder builder = new Request.Builder()
        .header(AUTH_HEADER, apiKey)
        .addHeader(ACCEPT_HEADER, "application/json");

    if (this.customHeaders != null && !this.customHeaders.isEmpty()) {
      this.customHeaders.forEach((k, v) -> builder.addHeader(k, v));
    }

    return builder;
  }

  /**
   * Returns a build request with GET.
   * @param url - URL to invoke
   * @return
   */
  @Override
  public Request newGetRequest(HttpUrl url) {
    final Request.Builder builder = newRequestBuilder();
    builder.url(url);

    return builder.build();
  }

  /**
   * Returns a build request with GET.
   * @param url - URL to invoke
   * @param body - body to post
   * @return
   */
  @Override
  public Request newPostRequest(HttpUrl url, RequestBody body) {
    final Request.Builder builder = newRequestBuilder();
    builder.url(url).post(body);

    return builder.build();
  }

  /**
   * Close the FlagsmithAPIWrapper instance, cleaning up any dependent threads or services
   * which need cleaning up before the instance can be fully destroyed.
   */
  public void close() {
    this.requestor.close();

    AnalyticsProcessor analyticsProcessor = this.getConfig().getAnalyticsProcessor();
    if (analyticsProcessor != null) {
      analyticsProcessor.close();
    }
  }

  /*
   * A DTO class to manage the response from the identities endpoint.
   */
  static class FlagsAndTraitsResponse {
    List<FeatureStateModel> flags;
    JsonNode traits;

    FlagsAndTraitsResponse(List<FeatureStateModel> flags, JsonNode traits) {
      this.flags = flags;
      this.traits = traits;
    }

    List<FeatureStateModel> getFlags() {
      return this.flags;
    }
  }
}
