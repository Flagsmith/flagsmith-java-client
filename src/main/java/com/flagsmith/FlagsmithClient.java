package com.flagsmith;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.Engine;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.Flags;
import com.flagsmith.threads.AnalyticsProcessor;
import com.flagsmith.threads.PollingManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.slf4j.LoggerFactory;

/**
 * A client for Flagsmith API.
 */
public class FlagsmithClient {

  private final FlagsmithLogger logger = new FlagsmithLogger();
  private FlagsmithSdk flagsmithSdk;
  private EnvironmentModel environment;
  private PollingManager pollingManager;
  private AnalyticsProcessor analyticsProcessor;

  private FlagsmithClient() { }

  public static FlagsmithClient.Builder newBuilder() {
    return new FlagsmithClient.Builder();
  }

  /**
   * Get user Trait from a given list of traits and trait key.
   *
   * @param key    a unique user trait key
   * @param traits list of traits
   * @return a Trait object or null if does not exist
   */
  private static TraitModel getTraitByKey(String key, List<TraitModel> traits) {
    if (traits != null) {
      for (TraitModel trait : traits) {
        if (trait.getTraitKey().equals(key)) {
          return trait;
        }
      }
    }
    return null;
  }

  /**
   * Get a list of user Traits from trait list and trait keys.
   *
   * @return a list of user Trait
   */
  private static List<TraitModel> getTraitsByKeys(List<TraitModel> traits, String[] keys) {
    // if no keys provided return all the user traits
    if (keys == null || keys.length == 0 || traits == null) {
      return traits;
    }

    // otherwise filter on give user traits keys
    List<TraitModel> filteredTraits = new ArrayList<>();
    for (TraitModel trait : traits) {
      if (Arrays.asList(keys).contains(trait.getTraitKey())) {
        filteredTraits.add(trait);
      }
    }
    return filteredTraits;
  }

  /**
   * Get user Trait from a given FlagsAndTraits and trait key.
   *
   * @param key            a unique user trait key
   * @param flagsAndTraits flags and traits object
   * @return a Trait object or null if does not exist
   */
  public TraitModel getTrait(FlagsAndTraits flagsAndTraits, String key) {
    if (flagsAndTraits == null) {
      return null;
    }
    return getTraitByKey(key, flagsAndTraits.getTraits());
  }

  /**
   * Get user Trait for given user identity and trait key.
   *
   * @param key  a unique user trait key
   * @param identifier a user in context
   * @return a Trait object or null if does not exist
   */
  public TraitModel getTrait(String identifier, String key) {
    List<TraitModel> traits = getUserTraits(identifier);
    return getTraitByKey(key, traits);
  }

  /**
   * Get a list of user Traits for user identity and trait keys.
   *
   * @param flagsAndTraits  the user's flags and traits
   * @param keys            the trait keys to filter for
   * @return a list of user Trait
   */
  public List<TraitModel> getTraits(FlagsAndTraits flagsAndTraits, String... keys) {
    return flagsAndTraits == null ? null : getTraitsByKeys(flagsAndTraits.getTraits(), keys);
  }

  /**
   * Get a list of user Traits for user identity and trait keys.
   *
   * @param identifier  the user to get traits for
   * @param keys  the trait keys to filter for      
   * @return a list of user Trait
   */
  public List<TraitModel> getTraits(String identifier, String... keys) {
    return getTraitsByKeys(getUserTraits(identifier), keys);
  }

  /**
   * Get a list of existing Features for the given environment.
   *
   * @return a list of feature flags
   */
  public List<FeatureStateModel> getFeatureFlags() {
    return getFeatureFlags(null);
  }

  /**
   * Get a list of existing Features for the given environment and user.
   *
   * @param identifier a user in context
   * @return a list of feature flags
   */
  public List<FeatureStateModel> getFeatureFlags(String identifier) {
    return getFeatureFlags(identifier, false);
  }

  /**
   * Get a list of existing Features for the given environment and user.
   *
   * @param identifier    a user in context
   * @param doThrow throw exceptions or fail silently
   * @return a list of feature flags
   */
  public List<FeatureStateModel> getFeatureFlags(String identifier, boolean doThrow) {
    return this.flagsmithSdk.getFeatureFlags(identifier, doThrow);
  }

  /**
   * Check if Feature flag exist and is enabled.
   *
   * @param featureId an identifier for the feature
   * @return true if feature flag exist and enabled, false otherwise
   */
  public boolean hasFeatureFlag(String featureId) {
    List<FeatureStateModel> featureFlags = getFeatureFlags();
    return hasFeatureFlagByName(featureId, featureFlags);
  }

  /**
   * Check if Feature flag exist and is enabled for given user.
   *
   * @param featureId a unique feature name identifier
   * @param identifier      a user in context
   * @return true if feature flag exist and enabled, false otherwise
   */
  public boolean hasFeatureFlag(String featureId, String identifier) {
    List<FeatureStateModel> featureFlags = getFeatureFlags(identifier);
    return hasFeatureFlagByName(featureId, featureFlags);
  }

  /**
   * Check if Feature flag exist and is enabled in a FlagsAndTraits.
   *
   * @param featureId      a unique feature name identifier
   * @param flagsAndTraits flags and traits object
   * @return true if feature flag exist and enabled, false otherwise
   */
  public boolean hasFeatureFlag(String featureId, FlagsAndTraits flagsAndTraits) {
    if (flagsAndTraits == null) {
      return evaluateDefaultFlagPredicate(featureId);
    }
    return hasFeatureFlagByName(featureId, flagsAndTraits.getFlags());
  }

  /**
   * Check if Feature flag exist and is enabled in a list of flags.
   *
   * @param featureId    a unique feature name identifier
   * @param featureFlags a list of flags
   * @return true if feature flag exist and enabled, false otherwise
   */
  private boolean hasFeatureFlagByName(String featureId, List<FeatureStateModel> featureFlags) {
    if (featureFlags != null) {
      for (FeatureStateModel flag : featureFlags) {
        if (flag.getFeature().getName().equals(featureId)) {
          return flag.getEnabled();
        }
      }
    }
    return evaluateDefaultFlagPredicate(featureId);
  }

  /**
   * Get Feature value (remote config) for given feature id.
   *
   * @param featureId a unique feature name identifier
   * @return a value for the Feature or null if feature does not exist
   */
  public String getFeatureFlagValue(String featureId) {
    List<FeatureStateModel> featureFlags = getFeatureFlags();
    return getFeatureFlagValueByName(featureId, featureFlags);
  }

  /**
   * Get Feature value (remote config) for given feature id and user.
   *
   * @param featureId a unique feature name identifier
   * @param identifier      a user in context
   * @return a value for the feature or null if does not exist
   */
  public String getFeatureFlagValue(String featureId, String identifier) {
    List<FeatureStateModel> featureFlags = getFeatureFlags(identifier);
    return getFeatureFlagValueByName(featureId, featureFlags);
  }

  /**
   * Get Feature value (remote config) for given feature id and user.
   *
   * @param featureId      a unique feature name identifier
   * @param flagsAndTraits flags and traits object
   * @return a value for the feature or null if does not exist
   */
  public String getFeatureFlagValue(String featureId, FlagsAndTraits flagsAndTraits) {
    if (flagsAndTraits == null) {
      return evaluateDefaultFlagValue(featureId);
    }
    return getFeatureFlagValueByName(featureId, flagsAndTraits.getFlags());
  }

  /**
   * Get the default feature flags. This method can be useful for your unit tests, to ensure you
   * have setup the defaults correctly.
   *
   * @return list of default flags, not fetched from Flagsmith
   */
  public List<FeatureStateModel> getDefaultFlags() {
    return this.flagsmithSdk.getConfig().getFlagsmithFlagDefaults().getDefaultFlags();
  }

  /**
   * Get Feature value (remote config) for given feature id from a provided flag list.
   *
   * @param featureId    a unique feature name identifier
   * @param featureFlags list of feature flags
   * @return a value for the Feature or null if feature does not exist
   */
  private String getFeatureFlagValueByName(String featureId, List<FeatureStateModel> featureFlags) {
    if (featureFlags != null) {
      for (FeatureStateModel flag : featureFlags) {
        if (flag.getFeature().getName().equals(featureId)) {
          return flag.getValue().toString();
        }
      }
    }

    return evaluateDefaultFlagValue(featureId);
  }

  /**
   * Get a list of existing user Traits for the given environment and identity user.
   *
   * @param identifier a user in context
   * @return a list of user Traits
   */
  private List<TraitModel> getUserTraits(String identifier) {
    return getUserFlagsAndTraits(identifier).getTraits();
  }

  /**
   * Get a list of existing user Traits and Flags for the given environment and identity user. It
   * fails silently if there is an error.
   *
   * @param identifier a user in context
   * @return a list of user Traits and Flags or empty FlagsAndTraits
   */
  public FlagsAndTraits getUserFlagsAndTraits(String identifier) {
    return getUserFlagsAndTraits(identifier, false);
  }

  /**
   * Get a list of existing user Traits and Flags for the given environment and identity user.
   *
   * @param identifier    a user in context
   * @param doThrow indicates if errors should throw an exception or fail silently
   * @return a list of user Traits and Flags
   */
  public FlagsAndTraits getUserFlagsAndTraits(String identifier, boolean doThrow) {
    return this.flagsmithSdk.getUserFlagsAndTraits(identifier, doThrow);
  }

  /**
   * Update user Trait for given user and Trait details.
   *
   * @param toUpdate a user trait to update
   * @param identifier     a user in context
   * @return a Trait object or null if does not exist
   */
  public TraitModel updateTrait(String identifier, TraitModel toUpdate) {
    return updateTrait(identifier, toUpdate, false);
  }

  /**
   * Update user Trait for given user and Trait details.
   *
   * @param toUpdate a user trait to update
   * @param identifier     a user in context
   * @param doThrow  throw exceptions or fail silently
   * @return a Trait object or null if does not exist
   */
  public TraitModel updateTrait(String identifier, TraitModel toUpdate, boolean doThrow) {
    return this.flagsmithSdk.postUserTraits(identifier, toUpdate, doThrow);
  }

  /**
   * Load the environment flags in the environment variable from the API.
   */
  public void updateEnvironment() {
    this.environment = flagsmithSdk.getEnvironment();
  }

  /**
   * Get all the default for flags for the current environment.
   *
   * @return
   */
  public Flags getEnvironmentFlags() throws FlagsmithApiError {
    if (environment != null) {
      return getEnvironmentFlagsFromDocument();
    }

    return getEnvironmentFlagsFromApi();
  }

  /**
   * Get all the flags for the current environment for a given identity. Will also
   * upsert all traits to the Flagsmith API for future evaluations. Providing a
   * trait with a value of None will remove the trait from the identity if it exists.
   *
   * @param identifier identifier string
   * @param traits list of key value traits
   * @return
   */
  public Flags getIdentityFlags(String identifier, Map<String, String> traits)
      throws FlagsmithClientError {
    if (environment != null) {
      return getIdentityFlagsFromDocument(identifier, traits);
    }

    return getIdentityFlagsFromApi(identifier, traits);
  }

  private Flags getEnvironmentFlagsFromDocument() {
    return Flags.fromFeatureStateModels(
        Engine.getEnvironmentFeatureStates(environment),
        analyticsProcessor,
        null,
        flagsmithSdk.getConfig().getFlagsmithFlagDefaults()
      );
  }

  private Flags getIdentityFlagsFromDocument(String identifier, Map<String, String> traits)
      throws FlagsmithClientError {
    IdentityModel identity = buildIdentityModel(identifier, traits);
    List<FeatureStateModel> featureStates = Engine.getIdentityFeatureStates(environment, identity);

    return Flags.fromFeatureStateModels(
        featureStates,
        analyticsProcessor,
        identity.getCompositeKey(),
        flagsmithSdk.getConfig().getFlagsmithFlagDefaults()
    );
  }

  private Flags getEnvironmentFlagsFromApi() throws FlagsmithApiError {
    try {
      List<FeatureStateModel> apiFlags = flagsmithSdk.getFeatureFlags(Boolean.TRUE);

      return Flags.fromApiFlags(
          apiFlags,
          analyticsProcessor,
          flagsmithSdk.getConfig().getFlagsmithFlagDefaults()
      );
    } catch (Exception e) {
      if (flagsmithSdk.getConfig().getFlagsmithFlagDefaults() != null) {
        Flags flags = new Flags();
        flags.setDefaultFlagHandler(flagsmithSdk.getConfig().getFlagsmithFlagDefaults());

        return flags;
      }

      throw new FlagsmithApiError("Failed to get feature flags.");
    }
  }

  private Flags getIdentityFlagsFromApi(String identifier, Map<String, String> traits)
      throws FlagsmithApiError {
    try {
      List<TraitModel> traitsList = traits.entrySet().stream().map((row) -> {
        TraitModel trait = new TraitModel();
        trait.setTraitValue(row.getValue());
        trait.setTraitKey(row.getKey());

        return trait;
      }).collect(Collectors.toList());

      FlagsAndTraits flagsAndTraits = flagsmithSdk.identifyUserWithTraits(
          identifier,
          traitsList,
          Boolean.TRUE
      );

      return Flags.fromApiFlags(
          flagsAndTraits,
          analyticsProcessor,
          flagsmithSdk.getConfig().getFlagsmithFlagDefaults()
      );
    } catch (Exception e) {
      if (flagsmithSdk.getConfig().getFlagsmithFlagDefaults() != null) {
        Flags flags = new Flags();
        flags.setDefaultFlagHandler(flagsmithSdk.getConfig().getFlagsmithFlagDefaults());

        return flags;
      }

      throw new FlagsmithApiError("Failed to get feature flags.");
    }
  }

  private IdentityModel buildIdentityModel(String identifier, Map<String, String> traits)
      throws FlagsmithClientError {
    if (environment == null) {
      throw new
          FlagsmithClientError("Unable to build identity model when no local environment present.");
    }

    List<TraitModel> traitsList = traits.entrySet().stream().map((entry) -> {
      TraitModel trait = new TraitModel();
      trait.setTraitKey(entry.getKey());
      trait.setTraitValue(entry.getValue());

      return trait;
    }).collect(Collectors.toList());

    IdentityModel identity = new IdentityModel();
    identity.setIdentityTraits(traitsList);
    identity.setEnvironmentApiKey(environment.getApiKey());
    identity.setIdentifier(identifier);

    return identity;
  }

  /**
   * Create or update a list of user Traits for given user identity.
   *
   * <p>Please note this will override any existing identity with given list.
   *
   * @param identifier   a user in context
   * @param traits a list of Trait object to be created or updated
   * @return a FlagsAndTraits object
   */
  public FlagsAndTraits identifyUserWithTraits(String identifier, List<TraitModel> traits) {
    return identifyUserWithTraits(identifier, traits, false);
  }

  /**
   * Create or update a list of user Traits for given user identity.
   *
   * <p>Please note this will override any existing identity with given list.
   *
   * @param identifier    a user in context
   * @param traits  a list of Trait object to be created or updated
   * @param doThrow throw exceptions or fail silently
   * @return a FlagsAndTraits object
   */
  public FlagsAndTraits identifyUserWithTraits(String identifier, List<TraitModel> traits,
      boolean doThrow) {
    return flagsmithSdk.identifyUserWithTraits(identifier, traits, doThrow);
  }

  /**
   * Returns a FlagsmithCache cache object that encapsulates methods to manipulate the cache.
   *
   * @return a FlagsmithCache if enabled, otherwise null.
   */
  public FlagsmithCache getCache() {
    return this.flagsmithSdk.getCache();
  }

  private boolean evaluateDefaultFlagPredicate(String featureId) {
    return this.flagsmithSdk.getConfig().getFlagsmithFlagDefaults()
        .evaluateDefaultFlagPredicate(featureId);
  }

  private String evaluateDefaultFlagValue(String featureId) {
    return this.flagsmithSdk.getConfig().getFlagsmithFlagDefaults()
        .evaluateDefaultFlagValue(featureId);
  }

  public static class Builder {

    private final FlagsmithClient client;
    private FlagsmithConfig configuration = FlagsmithConfig.newBuilder().build();
    private HashMap<String, String> customHeaders;
    private String apiKey;
    private FlagsmithCacheConfig cacheConfig;
    private PollingManager pollingManager;
    private AnalyticsProcessor analyticsProcessor;

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
     * When a flag does not exist in Flagsmith or there is an error, the SDK will return false by
     * default.
     *
     * <p>If you would like to override this default behaviour, you can use this method. By default
     * it will return false for any flags that it does not recognise. 
     *
     * @param defaultFlagPredicate the new predicate to use as default flag boolean values
     * @return the Builder
     */
    public Builder setDefaultFlagPredicate(@NonNull Predicate<String> defaultFlagPredicate) {
      this.configuration.getFlagsmithFlagDefaults().setDefaultFlagPredicate(defaultFlagPredicate);
      return this;
    }

    /**
     * When a flag does not exist in Flagsmith or there is an error, the SDK will return null by
     * default.
     *
     * <p>If you would like to override this default behaviour, you can use this method. By default
     * it will return null for any flags that it does not recognise.
     *
     * @param defaultFlagValueFunction the new function to use as default flag string values
     * @return the Builder
     */
    public Builder setDefaultFlagValueFunction(
        @NonNull Function<String, String> defaultFlagValueFunction) {
      this.configuration.getFlagsmithFlagDefaults()
          .setDefaultFlagValueFunc(defaultFlagValueFunction);
      return this;
    }

    /**
     * When a flag does not exist in Flagsmith or there is an error, the SDK will return an empty
     * list of flags by default. For example: if you call identifyUserWithTraits(...) and the call
     * fails it will return an empty list of flags.
     *
     * <p>If you would like the SDK to return a default list of flags with default values, you can
     * set the default flag names with this method. For example: if you set a default flag with the
     * name "my-flag" using setDefaultFeatureFlags(["my-flag"]), and if you call
     * identifyUserWithTraits(...) and the call fails; identifyUserWithTraits(...) will return a
     * list of flags with 1 flag called "my-flag" and with default values configured with the
     * methods setDefaultFlagPredicate() and setDefaultFlagValueFunction().
     *
     * <p>Default: empty set;
     *
     * @param defaultFeatureFlags list of flag names
     * @return the Builder
     */
    public Builder setDefaultFeatureFlags(@NonNull Set<String> defaultFeatureFlags) {
      this.configuration.getFlagsmithFlagDefaults().setDefaultFeatureFlags(defaultFeatureFlags);
      return this;
    }

    /**
     * Enables logging, the project importing this module must include an implementation slf4j in
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
     * Enables logging, the project importing this module must include an implementation slf4j in
     * their pom.
     *
     * @return the Builder
     */
    public Builder enableLogging() {
      this.client.logger.setLogger(LoggerFactory.getLogger(FlagsmithClient.class));
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
     * <p>If no other cache configuration is set, the Caffeine defaults will be used, i.e. no limit
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
     * @return
     */
    public Builder withPollingManager(PollingManager manager) {
      pollingManager = manager;
      return this;
    }

    /**
     * Set the analytics processor.
     *
     * @param processor analytics processor object
     * @return
     */
    public Builder withAnalyticsProcessor(AnalyticsProcessor processor) {
      analyticsProcessor = processor;
      return this;
    }

    /**
     * Builds a FlagsmithClient.
     *
     * @return a FlagsmithClient
     */
    public FlagsmithClient build() {
      final FlagsmithApiWrapper flagsmithApiWrapper = new FlagsmithApiWrapper(
          this.configuration,
          this.customHeaders,
          client.logger,
          apiKey
      );

      if (cacheConfig != null) {
        client.flagsmithSdk = new FlagsmithCachedApiWrapper(
            cacheConfig.getCache(), flagsmithApiWrapper);
      } else {
        client.flagsmithSdk = flagsmithApiWrapper;
      }

      if (configuration.getEnableAnalytics()) {
        if (this.analyticsProcessor != null) {
          client.analyticsProcessor = analyticsProcessor;
        } else {
          client.analyticsProcessor = new AnalyticsProcessor(
              flagsmithApiWrapper,
              configuration.getHttpClient(),
              client.logger
          );
        }
      }

      if (configuration.getEnableLocalEvaluation()) {
        if (this.pollingManager != null) {
          client.pollingManager = pollingManager;
        } else {
          client.pollingManager = new PollingManager(client);
        }

        client.pollingManager.startPolling();
      }

      return this.client;
    }
  }
}
