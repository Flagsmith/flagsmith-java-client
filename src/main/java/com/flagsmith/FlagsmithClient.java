package com.flagsmith;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import com.flagsmith.flagengine.Engine;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flags;
import com.flagsmith.models.Segment;
import com.flagsmith.threads.PollingManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private EnvironmentModel environment;
  private PollingManager pollingManager;
  private Map<String, IdentityModel> identitiesWithOverridesByIdentifier;

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
      EnvironmentModel updatedEnvironment = flagsmithSdk.getEnvironment();

      // if we didn't get an environment from the API,
      // then don't overwrite the copy we already have.
      if (updatedEnvironment != null) {
        List<IdentityModel> identityOverrides = updatedEnvironment.getIdentityOverrides();

        if (identityOverrides != null) {
          Map<String, IdentityModel> identitiesWithOverridesByIdentifier = new HashMap<>();
          for (IdentityModel identity : identityOverrides) {
            identitiesWithOverridesByIdentifier.put(identity.getIdentifier(), identity);
          }
          this.identitiesWithOverridesByIdentifier = identitiesWithOverridesByIdentifier;
        }

        this.environment = updatedEnvironment;
      } else {
        logger.error(getEnvironmentUpdateErrorMessage());
      }
    } catch (RuntimeException e) {
      logger.error(getEnvironmentUpdateErrorMessage());
    }
  }

  /**
   * Get all the default for flags for the current environment.
   */
  public Flags getEnvironmentFlags() throws FlagsmithClientError {
    if (getShouldUseEnvironmentDocument()) {
      return getEnvironmentFlagsFromDocument();
    }

    return getEnvironmentFlagsFromApi();
  }

  /**
   * Get all the flags for the current environment for a given identity. Will also
   * upsert all traits to the Flagsmith API for future evaluations. Providing a
   * trait with a value of None will remove the trait from the identity if it
   * exists.
   *
   * @param identifier identifier string
   */
  public Flags getIdentityFlags(String identifier)
      throws FlagsmithClientError {
    return getIdentityFlags(identifier, new HashMap<>());
  }

  /**
   * Get all the flags for the current environment for a given identity. Will also
   * upsert all traits to the Flagsmith API for future evaluations. Providing a
   * trait with a value of None will remove the trait from the identity if it
   * exists.
   *
   * @param identifier identifier string
   * @param traits     list of key value traits
   */
  public Flags getIdentityFlags(String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    if (getShouldUseEnvironmentDocument()) {
      return getIdentityFlagsFromDocument(identifier, traits);
    }

    return getIdentityFlagsFromApi(identifier, traits);
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
    if (environment == null) {
      throw new FlagsmithClientError("Local evaluation required to obtain identity segments.");
    }
    IdentityModel identityModel = getIdentityModel(
        identifier, (traits != null ? traits : new HashMap<>()));
    List<SegmentModel> segmentModels = SegmentEvaluator.getIdentitySegments(
        environment, identityModel);

    return segmentModels.stream().map((segmentModel) -> {
      Segment segment = new Segment();
      segment.setId(segmentModel.getId());
      segment.setName(segmentModel.getName());

      return segment;
    }).collect(Collectors.toList());
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

  private Flags getEnvironmentFlagsFromDocument() throws FlagsmithClientError {
    if (environment == null) {
      if (getConfig().getFlagsmithFlagDefaults() == null) {
        throw new FlagsmithClientError("Unable to get flags. No environment present.");
      }
      return getDefaultFlags();
    }

    return Flags.fromFeatureStateModels(
        Engine.getEnvironmentFeatureStates(environment),
        getConfig().getAnalyticsProcessor(),
        null,
        getConfig().getFlagsmithFlagDefaults());
  }

  private Flags getIdentityFlagsFromDocument(String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    if (environment == null) {
      if (getConfig().getFlagsmithFlagDefaults() == null) {
        throw new FlagsmithClientError("Unable to get flags. No environment present.");
      }
      return getDefaultFlags();
    }

    IdentityModel identity = getIdentityModel(identifier, traits);
    List<FeatureStateModel> featureStates = Engine.getIdentityFeatureStates(environment, identity);

    return Flags.fromFeatureStateModels(
        featureStates,
        getConfig().getAnalyticsProcessor(),
        identity.getCompositeKey(),
        getConfig().getFlagsmithFlagDefaults());
  }

  private Flags getEnvironmentFlagsFromApi() throws FlagsmithApiError {
    try {
      return flagsmithSdk.getFeatureFlags(Boolean.TRUE);
    } catch (Exception e) {
      if (getConfig().getFlagsmithFlagDefaults() != null) {
        return getDefaultFlags();
      } else if (environment != null) {
        try {
          return getEnvironmentFlagsFromDocument();
        } catch (FlagsmithClientError ce) {
          // Do nothing and fall through to FlagsmithApiError
        }
      }

      throw new FlagsmithApiError("Failed to get feature flags.");
    }
  }

  private Flags getIdentityFlagsFromApi(String identifier, Map<String, Object> traits)
      throws FlagsmithApiError {
    try {
      List<TraitModel> traitsList = traits.entrySet().stream().map((row) -> {
        TraitModel trait = new TraitModel();
        trait.setTraitValue(row.getValue());
        trait.setTraitKey(row.getKey());

        return trait;
      }).collect(Collectors.toList());

      return flagsmithSdk.identifyUserWithTraits(
          identifier,
          traitsList,
          Boolean.TRUE);
    } catch (Exception e) {
      if (getConfig().getFlagsmithFlagDefaults() != null) {
        return getDefaultFlags();
      } else if (environment != null) {
        try {
          return getIdentityFlagsFromDocument(identifier, traits);
        } catch (FlagsmithClientError ce) {
          // Do nothing and fall through to FlagsmithApiError
        }
      }

      throw new FlagsmithApiError("Failed to get feature flags.");
    }
  }

  private IdentityModel getIdentityModel(String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    if (environment == null) {
      throw new FlagsmithClientError(
          "Unable to build identity model when no local environment present.");
    }

    List<TraitModel> traitsList = traits.entrySet().stream().map((entry) -> {
      TraitModel trait = new TraitModel();
      trait.setTraitKey(entry.getKey());
      trait.setTraitValue(entry.getValue());

      return trait;
    }).collect(Collectors.toList());

    if (identitiesWithOverridesByIdentifier != null) {
      IdentityModel identityOverride = identitiesWithOverridesByIdentifier.get(identifier);
      if (identityOverride != null) {
        identityOverride.updateTraits(traitsList);
        return identityOverride;
      }
    }

    IdentityModel identity = new IdentityModel();
    identity.setIdentityTraits(traitsList);
    identity.setEnvironmentApiKey(environment.getApiKey());
    identity.setIdentifier(identifier);

    return identity;
  }

  private Flags getDefaultFlags() {
    Flags flags = new Flags();
    flags.setDefaultFlagHandler(getConfig().getFlagsmithFlagDefaults());
    return flags;
  }

  private String getEnvironmentUpdateErrorMessage() {
    if (this.environment == null) {
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
        client.environment = configuration.getOfflineHandler().getEnvironment();
      }

      return this.client;
    }
  }
}
