package com.flagsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import com.flagsmith.flagengine.Engine;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.EvaluationResult;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.mappers.EngineMappers;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flags;
import com.flagsmith.models.Segment;
import com.flagsmith.models.SegmentMetadata;
import com.flagsmith.threads.PollingManager;
import com.flagsmith.utils.ModelUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for Flagsmith API.
 */
@Data
public class FlagsmithClient {

  private final FlagsmithLogger logger = new FlagsmithLogger();
  private FlagsmithSdk flagsmithSdk;
  private EvaluationContext evaluationContext;
  private PollingManager pollingManager;

  private FlagsmithClient() {
  }

  public static FlagsmithClient.Builder newBuilder() {
    return new FlagsmithClient.Builder();
  }

  /**
   * Load the environment flags in the environment variable from the API.
   */
  public void updateEnvironment() {
    try {
      EvaluationContext updatedEvaluationContext = flagsmithSdk.getEvaluationContext();

      // if we didn't get an environment from the API,
      // then don't overwrite the copy we already have.
      if (updatedEvaluationContext != null) {
        this.evaluationContext = updatedEvaluationContext;
      } else {
        logger.error(getEnvironmentUpdateErrorMessage());
      }
    } catch (RuntimeException e) {
      logger.error(getEnvironmentUpdateErrorMessage());
    }
  }

  /**
   * Get all the default for flags for the current environment.
   *
   * @return environment flags
   */
  public Flags getEnvironmentFlags() throws FlagsmithClientError {
    if (getShouldUseEnvironmentDocument()) {
      return getEnvironmentFlagsFromEvaluationContext();
    }

    return getEnvironmentFlagsFromApi();
  }

  /**
   * Get all the flags for the current environment for a given identity.
   *
   * @param identifier identifier string
   * @return result of flag evaluation for given identity
   */
  public Flags getIdentityFlags(String identifier)
      throws FlagsmithClientError {
    return getIdentityFlags(identifier, new HashMap<>());
  }

  /**
   * Get all the flags for the current environment for a given identity. Will also
   * upsert traits to the Flagsmith API for future evaluations.
   * 
   * <p>
   * A trait with a value of null will remove the trait from the identity if it
   * exists.
   * </p>
   * <p>
   * To specify a transient trait, use the TraitConfig class with isTransient set
   * to true as the trait value.
   * </p>
   *
   * @see com.flagsmith.models.TraitConfig
   * 
   * @param identifier identifier string
   * @param traits     a map of trait keys to trait values
   */
  public Flags getIdentityFlags(String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    return getIdentityFlags(identifier, traits, false);
  }

  /**
   * Get all the flags for the current environment for a given identity. Will also
   * upsert traits to the Flagsmith API for future evaluations, if isTransient set
   * to false.
   * 
   * <p>
   * A trait with a value of null will remove the trait from the identity if it
   * exists.
   * </p>
   * <p>
   * To specify a transient trait, use the TraitConfig class with isTransient set
   * to true as the trait value.
   * </p>
   *
   * @see com.flagsmith.models.TraitConfig
   * 
   * @param identifier  identifier string
   * @param traits      a map of trait keys to trait values
   * @param isTransient set to true to prevent identity persistence
   * @return result of flag evaluation for given identity
   */
  public Flags getIdentityFlags(String identifier, Map<String, Object> traits, boolean isTransient)
      throws FlagsmithClientError {
    if (getShouldUseEnvironmentDocument()) {
      return getIdentityFlagsFromEvaluationContext(identifier, traits);
    }

    return getIdentityFlagsFromApi(identifier, traits, isTransient);
  }

  /**
   * Get a list of segments that the given identity is in.
   *
   * @param identifier a unique identifier for the identity in the current
   *                   environment, e.g. email address, username, uuid
   */
  public List<Segment> getIdentitySegments(String identifier)
      throws FlagsmithClientError {
    return getIdentitySegments(identifier, null);
  }

  /**
   * Get a list of segments that the given identity is in.
   *
   * @param identifier a unique identifier for the identity in the current
   *                   environment, e.g. email address, username, uuid
   * @param traits     a dictionary of traits to add / update on the identity in
   *                   Flagsmith, e.g. {"num_orders": 10}
   */
  public List<Segment> getIdentitySegments(String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    if (evaluationContext == null) {
      throw new FlagsmithClientError("Local evaluation required to obtain identity segments.");
    }

    final EvaluationContext context = EngineMappers.mapContextAndIdentityDataToContext(
        evaluationContext, identifier, traits);

    final EvaluationResult result = Engine.getEvaluationResult(context);

    ObjectMapper mapper = MapperFactory.getMapper();

    return result.getSegments().stream().map((segmentModel) -> {
      if (segmentModel.getMetadata() == null) {
        return null;
      }

      SegmentMetadata segmentMetadata = mapper.convertValue(
          segmentModel.getMetadata(), SegmentMetadata.class);

      Integer flagsmithId = segmentMetadata.getFlagsmithId();
      if (segmentMetadata.getSource() != SegmentMetadata.Source.API
          || flagsmithId == null) {
        return null;
      }

      Segment segment = new Segment();
      segment.setId(flagsmithId);
      segment.setName(segmentModel.getName());

      return segment;
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }

  /**
   * Should be called when terminating the client to clean up any resources that
   * need cleaning up.
   **/
  public void close() {
    if (pollingManager != null) {
      pollingManager.stopPolling();
    }
    flagsmithSdk.close();
  }

  private Flags getEnvironmentFlagsFromEvaluationContext() throws FlagsmithClientError {
    if (evaluationContext == null) {
      if (getConfig().getFlagsmithFlagDefaults() == null) {
        throw new FlagsmithClientError("Unable to get flags. No environment present.");
      }
      return getDefaultFlags();
    }

    final EvaluationResult result = Engine.getEvaluationResult(evaluationContext);

    return Flags.fromEvaluationResult(
        result,
        getConfig().getAnalyticsProcessor(),
        getConfig().getFlagsmithFlagDefaults());
  }

  private Flags getIdentityFlagsFromEvaluationContext(
      String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    if (evaluationContext == null) {
      if (getConfig().getFlagsmithFlagDefaults() == null) {
        throw new FlagsmithClientError("Unable to get flags. No environment present.");
      }
      return getDefaultFlags();
    }

    final EvaluationContext context = EngineMappers.mapContextAndIdentityDataToContext(
        evaluationContext, identifier, traits);

    final EvaluationResult result = Engine.getEvaluationResult(context);

    return Flags.fromEvaluationResult(
        result,
        getConfig().getAnalyticsProcessor(),
        getConfig().getFlagsmithFlagDefaults());
  }

  private Flags getEnvironmentFlagsFromApi() throws FlagsmithApiError {
    try {
      return flagsmithSdk.getFeatureFlags(Boolean.TRUE);
    } catch (Exception e) {
      if (getConfig().getFlagsmithFlagDefaults() != null) {
        return getDefaultFlags();
      } else if (evaluationContext != null) {
        try {
          return getEnvironmentFlagsFromEvaluationContext();
        } catch (FlagsmithClientError ce) {
          // Do nothing and fall through to FlagsmithApiError
        }
      }

      throw new FlagsmithApiError("Failed to get feature flags.");
    }
  }

  private Flags getIdentityFlagsFromApi(
      String identifier, Map<String, Object> traits, boolean isTransient)
      throws FlagsmithApiError {
    try {
      return flagsmithSdk.identifyUserWithTraits(
          identifier,
          ModelUtils.getSdkTraitModelsFromTraitMap(traits),
          isTransient,
          Boolean.TRUE);
    } catch (Exception e) {
      if (getConfig().getFlagsmithFlagDefaults() != null) {
        return getDefaultFlags();
      } else if (evaluationContext != null) {
        try {
          return getIdentityFlagsFromEvaluationContext(identifier, traits);
        } catch (FlagsmithClientError ce) {
          // Do nothing and fall through to FlagsmithApiError
        }
      }

      throw new FlagsmithApiError("Failed to get feature flags.");
    }
  }

  private Flags getDefaultFlags() {
    Flags flags = new Flags();
    flags.setDefaultFlagHandler(getConfig().getFlagsmithFlagDefaults());
    return flags;
  }

  private String getEnvironmentUpdateErrorMessage() {
    if (this.evaluationContext == null) {
      return "Unable to update environment from API. "
          + "No environment configured - using defaultHandler if configured.";
    } else {
      return "Unable to update environment from API. Continuing to use previous copy.";
    }
  }

  private FlagsmithConfig getConfig() {
    return flagsmithSdk.getConfig();
  }

  /**
   * Returns a FlagsmithCache cache object that encapsulates methods to manipulate
   * the cache.
   *
   * @return a FlagsmithCache if enabled, otherwise null.
   */
  public FlagsmithCache getCache() {
    return this.flagsmithSdk.getCache();
  }

  /**
   * Returns a boolean indicating whether the flags should be retrieved from a
   * locally stored environment document instead of retrieved from the API.
   */
  private Boolean getShouldUseEnvironmentDocument() {
    FlagsmithConfig config = getConfig();
    return config.getEnableLocalEvaluation() | config.getOfflineMode();
  }

  public static class Builder {

    private final FlagsmithClient client;
    private FlagsmithConfig configuration = FlagsmithConfig.newBuilder().build();
    private HashMap<String, String> customHeaders;
    private String apiKey;
    private FlagsmithCacheConfig cacheConfig;
    private PollingManager pollingManager;
    private FlagsmithApiWrapper flagsmithApiWrapper;

    private Builder() {
      client = new FlagsmithClient();
    }

    /**
     * Set the environment API key.
     *
     * @param apiKey the api key for environment
     * @return the Builder
     */
    public Builder setApiKey(String apiKey) {
      if (null == apiKey) {
        throw new IllegalArgumentException("Api key can not be null");
      } else {
        this.apiKey = apiKey;
        return this;
      }
    }

    /**
     * When a flag does not exist in Flagsmith or there is an error, the SDK will
     * return null by
     * default.
     *
     * <p>If you would like to override this default behaviour, you can use this
     * method. By default
     * it will return null for any flags that it does not recognise.
     *
     * @param defaultFlagValueFunction the new function to use as default flag
     *                                 values
     * @return the Builder
     */
    public Builder setDefaultFlagValueFunction(
        @NonNull Function<String, BaseFlag> defaultFlagValueFunction) {
      if (this.configuration.getFlagsmithFlagDefaults() == null) {
        this.configuration.setFlagsmithFlagDefaults(new FlagsmithFlagDefaults());
      }
      this.configuration.getFlagsmithFlagDefaults()
          .setDefaultFlagValueFunc(defaultFlagValueFunction);
      return this;
    }

    /**
     * Enables logging, the project importing this module must include an
     * implementation slf4j in
     * their pom.
     *
     * @param level log error level.
     * @return the Builder
     */
    public Builder enableLogging(FlagsmithLoggerLevel level) {
      this.client.logger.setLogger(LoggerFactory.getLogger(FlagsmithClient.class), level);
      return this;
    }

    /**
     * Enables logging, the project importing this module must include an
     * implementation slf4j in
     * their pom.
     *
     * @return the Builder
     */
    public Builder enableLogging() {
      this.client.logger.setLogger(LoggerFactory.getLogger(FlagsmithClient.class));
      return this;
    }

    /**
     * Enables logging, the project importing this module must include an
     * implementation slf4j in
     * their pom.
     *
     * @return the Builder
     */
    public Builder enableLogging(Logger logger) {
      this.client.logger.setLogger(logger);
      return this;
    }

    /**
     * Override default FlagsmithConfig for Flagsmith API.
     *
     * @param config an FlagsmithConfig to override default one.
     * @return the Builder
     */
    public Builder withConfiguration(FlagsmithConfig config) {
      if (config != null) {
        this.configuration = config;
      }
      return this;
    }

    /**
     * Set the base URL for Flagsmith API, overriding default one.
     *
     * @param apiUrl the new base URI for the API.
     * @return the Builder
     */
    public Builder withApiUrl(String apiUrl) {
      if (apiUrl != null) {
        this.configuration = FlagsmithConfig.newBuilder()
            .baseUri(apiUrl)
            .build();
      }
      return this;
    }

    /**
     * Add custom HTTP headers to the calls.
     *
     * @param customHeaders headers.
     * @return the Builder
     */
    public Builder withCustomHttpHeaders(HashMap<String, String> customHeaders) {
      this.customHeaders = customHeaders;
      return this;
    }

    /**
     * Enable in-memory caching for the Flagsmith API.
     *
     * <p>If no other cache configuration is set, the Caffeine defaults will be used,
     * i.e. no limit
     *
     * @param cacheConfig an FlagsmithCacheConfig.
     * @return the Builder
     */
    public Builder withCache(FlagsmithCacheConfig cacheConfig) {
      this.cacheConfig = cacheConfig;
      return this;
    }

    /**
     * Set the polling manager.
     *
     * @param manager polling manager object
     */
    public Builder withPollingManager(PollingManager manager) {
      this.pollingManager = manager;
      return this;
    }

    /**
     * Set the api wrapper.
     *
     * @param flagsmithApiWrapper FlagsmithAPIWrapper object
     * @return the Builder
     */
    public Builder withFlagsmithApiWrapper(FlagsmithApiWrapper flagsmithApiWrapper) {
      this.flagsmithApiWrapper = flagsmithApiWrapper;
      return this;
    }

    /**
     * Builds a FlagsmithClient.
     *
     * @return a FlagsmithClient
     */
    public FlagsmithClient build() {
      if (configuration.getOfflineMode()) {
        if (configuration.getOfflineHandler() == null) {
          throw new FlagsmithRuntimeError("Offline handler must be provided to use offline mode.");
        }
      }

      if (this.flagsmithApiWrapper != null) {
        client.flagsmithSdk = this.flagsmithApiWrapper;
      } else if (cacheConfig != null) {
        client.flagsmithSdk = new FlagsmithApiWrapper(
            cacheConfig.getCache(),
            this.configuration,
            this.customHeaders,
            client.logger,
            apiKey);
      } else {
        client.flagsmithSdk = new FlagsmithApiWrapper(
            this.configuration,
            this.customHeaders,
            client.logger,
            apiKey);
      }

      if (configuration.getAnalyticsProcessor() != null) {
        configuration.getAnalyticsProcessor().setApi(client.flagsmithSdk);
        configuration.getAnalyticsProcessor().setLogger(client.logger);
      }

      if (configuration.getEnableLocalEvaluation()) {
        if (configuration.getOfflineHandler() != null) {
          throw new FlagsmithRuntimeError(
              "Local evaluation and offline handler cannot be used together.");
        }

        if (!apiKey.startsWith("ser.")) {
          throw new FlagsmithRuntimeError(
              "In order to use local evaluation, please generate a server key "
                  + "in the environment settings page.");
        }

        if (this.pollingManager != null) {
          client.pollingManager = pollingManager;
        } else {
          client.pollingManager = new PollingManager(
              client,
              configuration.getEnvironmentRefreshIntervalSeconds());
        }

        client.pollingManager.startPolling();
      }

      if (configuration.getOfflineHandler() != null) {
        if (configuration.getFlagsmithFlagDefaults() != null) {
          throw new FlagsmithRuntimeError(
              "Cannot use both default flag handler and offline handler.");
        }
        client.evaluationContext = EngineMappers.mapEnvironmentToContext(
          configuration.getOfflineHandler().getEnvironment());
      }

      return this.client;
    }
  }
}
