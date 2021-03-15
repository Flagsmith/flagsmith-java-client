package com.flagsmith;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * A client for Flagsmith API.
 */
public class FlagsmithClient {

    private final FlagsmithLogger logger = new FlagsmithLogger();
    private FlagsmithSDK flagsmithSDK;

    private FlagsmithClient() {
    }

    /**
     * Get a list of existing Features for the given environment
     *
     * @return a list of feature flags
     */
    public List<Flag> getFeatureFlags() {
        return getFeatureFlags(null);
    }

    /**
     * Get a list of existing Features for the given environment and user
     *
     * @param user a user in context
     * @return a list of feature flags
     */
    public List<Flag> getFeatureFlags(FeatureUser user) {
        return getFeatureFlags(user, false);
    }

    /**
     * Get a list of existing Features for the given environment and user
     *
     * @param user a user in context
     * @param doThrow throw exceptions or fail silently
     * @return a list of feature flags
     */
    public List<Flag> getFeatureFlags(FeatureUser user, boolean doThrow) {
        return this.flagsmithSDK.getFeatureFlags(user, doThrow).getFlags();
    }

    /**
     * Check if Feature flag exist and is enabled
     *
     * @param featureId an identifier for the feature
     * @return true if feature flag exist and enabled, false otherwise
     */
    public boolean hasFeatureFlag(String featureId) {
        List<Flag> featureFlags = getFeatureFlags();
        return hasFeatureFlagByName(featureId, featureFlags);
    }

    /**
     * Check if Feature flag exist and is enabled for given user
     *
     * @param featureId a unique feature name identifier
     * @param user      a user in context
     * @return true if feature flag exist and enabled, false otherwise
     */
    public boolean hasFeatureFlag(String featureId, FeatureUser user) {
        List<Flag> featureFlags = getFeatureFlags(user);
        return hasFeatureFlagByName(featureId, featureFlags);
    }

    /**
     * Check if Feature flag exist and is enabled in a FlagsAndTraits
     *
     * @param featureId      a unique feature name identifier
     * @param flagsAndTraits flags and traits object
     * @return true if feature flag exist and enabled, false otherwise
     */
    public static boolean hasFeatureFlag(String featureId, FlagsAndTraits flagsAndTraits) {
        if (flagsAndTraits == null) {
            return false;
        }
        return hasFeatureFlagByName(featureId, flagsAndTraits.getFlags());
    }

    /**
     * Check if Feature flag exist and is enabled in a list of flags
     *
     * @param featureId    a unique feature name identifier
     * @param featureFlags a list of flags
     * @return true if feature flag exist and enabled, false otherwise
     */
    private static boolean hasFeatureFlagByName(String featureId, List<Flag> featureFlags) {
        if (featureFlags != null) {
            for (Flag flag : featureFlags) {
                if (flag.getFeature().getName().equals(featureId) && flag.isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get Feature value (remote config) for given feature id.
     *
     * @param featureId a unique feature name identifier
     * @return a value for the Feature or null if feature does not exist
     */
    public String getFeatureFlagValue(String featureId) {
        List<Flag> featureFlags = getFeatureFlags();
        return getFeatureFlagValueByName(featureId, featureFlags);
    }

    /**
     * Get Feature value (remote config) for given feature id and user
     *
     * @param featureId a unique feature name identifier
     * @param user      a user in context
     * @return a value for the feature or null if does not exist
     */
    public String getFeatureFlagValue(String featureId, FeatureUser user) {
        List<Flag> featureFlags = getFeatureFlags(user);
        return getFeatureFlagValueByName(featureId, featureFlags);
    }

    /**
     * Get Feature value (remote config) for given feature id and user
     *
     * @param featureId      a unique feature name identifier
     * @param flagsAndTraits flags and traits object
     * @return a value for the feature or null if does not exist
     */
    public static String getFeatureFlagValue(String featureId, FlagsAndTraits flagsAndTraits) {
        if (flagsAndTraits == null) {
            return null;
        }
        return getFeatureFlagValueByName(featureId, flagsAndTraits.getFlags());
    }

    /**
     * Get Feature value (remote config) for given feature id from a provided flag list.
     *
     * @param featureId    a unique feature name identifier
     * @param featureFlags list of feature flags
     * @return a value for the Feature or null if feature does not exist
     */
    private static String getFeatureFlagValueByName(String featureId, List<Flag> featureFlags) {
        if (featureFlags != null) {
            for (Flag flag : featureFlags) {
                if (flag.getFeature().getName().equals(featureId)) {
                    return flag.getStateValue();
                }
            }
        }

        return null;
    }

    /**
     * Get user Trait for given user identity and trait key.
     *
     * @param key  a unique user trait key
     * @param user a user in context
     * @return a Trait object or null if does not exist
     */
    public Trait getTrait(FeatureUser user, String key) {
        List<Trait> traits = getUserTraits(user);
        return getTraitByKey(key, traits);
    }

    /**
     * Get user Trait from a given FlagsAndTraits and trait key.
     *
     * @param key            a unique user trait key
     * @param flagsAndTraits flags and traits object
     * @return a Trait object or null if does not exist
     */
    public static Trait getTrait(FlagsAndTraits flagsAndTraits, String key) {
        if (flagsAndTraits == null) {
            return null;
        }
        return getTraitByKey(key, flagsAndTraits.getTraits());
    }

    /**
     * Get user Trait from a given list of traits and trait key.
     *
     * @param key    a unique user trait key
     * @param traits list of traits
     * @return a Trait object or null if does not exist
     */
    private static Trait getTraitByKey(String key, List<Trait> traits) {
        if (traits != null) {
            for (Trait trait : traits) {
                if (trait.getKey().equals(key)) {
                    return trait;
                }
            }
        }
        return null;
    }

    /**
     * Get a list of user Traits for user identity and trait keys
     *
     * @return a list of user Trait
     */
    public List<Trait> getTraits(FeatureUser user, String... keys) {
        List<Trait> traits = getUserTraits(user);
        return getTraitsByKeys(traits, keys);
    }

    /**
     * Get a list of user Traits for user identity and trait keys
     *
     * @return a list of user Trait
     */
    public static List<Trait> getTraits(FlagsAndTraits flagsAndTraits, String... keys) {
        if (flagsAndTraits == null) {
            return null;
        }
        return getTraitsByKeys(flagsAndTraits.getTraits(), keys);
    }

    /**
     * Get a list of user Traits from trait list and trait keys
     *
     * @return a list of user Trait
     */
    private static List<Trait> getTraitsByKeys(List<Trait> traits, String[] keys) {
        // if no keys provided return all the user traits
        if (keys == null || keys.length == 0 || traits == null) {
            return traits;
        }

        // otherwise filter on give user traits keys
        List<Trait> filteredTraits = new ArrayList<>();
        for (Trait trait : traits) {
            if (Arrays.asList(keys).contains(trait.getKey())) {
                filteredTraits.add(trait);
            }
        }
        return filteredTraits;
    }

    /**
     * Get a list of existing user Traits for the given environment and identity user
     *
     * @param user a user in context
     * @return a list of user Traits
     */
    private List<Trait> getUserTraits(FeatureUser user) {
        return getUserFlagsAndTraits(user).getTraits();
    }

    /**
     * Get a list of existing user Traits and Flags for the given environment and identity user
     * It fails silently if there is an error
     *
     * @param user a user in context
     * @return a list of user Traits and Flags or empty FlagsAndTraits
     */
    public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user) {
        return getUserFlagsAndTraits(user, false);
    }

    /**
     * Get a list of existing user Traits and Flags for the given environment and identity user
     *
     * @param user a user in context
     * @param doThrow indicates if errors should throw an exception or fail silently
     * @return a list of user Traits and Flags
     */
    public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
        return this.flagsmithSDK.getUserFlagsAndTraits(user, doThrow);
    }

    /**
     * Update user Trait for given user and Trait details.
     *
     * @param toUpdate a user trait to update
     * @param user     a user in context
     * @return a Trait object or null if does not exist
     */
    public Trait updateTrait(FeatureUser user, Trait toUpdate) {
        return updateTrait(user, toUpdate, false);
    }

    /**
     * Update user Trait for given user and Trait details.
     *
     * @param toUpdate a user trait to update
     * @param user     a user in context
     * @param doThrow  throw exceptions or fail silently
     * @return a Trait object or null if does not exist
     */
    public Trait updateTrait(FeatureUser user, Trait toUpdate, boolean doThrow) {
        return this.flagsmithSDK.postUserTraits(user, toUpdate, doThrow);
    }

    /**
     * <p>
     * Create or update a list of user Traits for given user identity.
     * </p>
     * <p>
     * Please note this will override any existing identity with given list.
     * </p>
     *
     * @param user   a user in context
     * @param traits a list of Trait object to be created or updated
     * @return a list of added Trait objects
     */
    public List<Trait> identifyUserWithTraits(FeatureUser user, List<Trait> traits) {
        return identifyUserWithTraits(user, traits, false);
    }

    /**
     * <p>
     * Create or update a list of user Traits for given user identity.
     * </p>
     * <p>
     * Please note this will override any existing identity with given list.
     * </p>
     *
     * @param user   a user in context
     * @param traits a list of Trait object to be created or updated
     * @param doThrow throw exceptions or fail silently
     * @return a list of added Trait objects
     */
    public List<Trait> identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow) {
        return flagsmithSDK.identifyUserWithTraits(user, traits, doThrow).getTraits();
    }

    public FlagsmithCache getCache() {
        return this.flagsmithSDK.getCache();
    }

    public static FlagsmithClient.Builder newBuilder() {
        return new FlagsmithClient.Builder();
    }

    public static class Builder {
        private FlagsmithClient client;
        private FlagsmithConfig configuration = FlagsmithConfig.newBuilder().build();
        private HashMap<String, String> customHeaders;
        private String apiKey;
        private FlagsmithCacheConfig cacheConfig;

        private Builder() {
            client = new FlagsmithClient();
        }

        /**
         * Set the environment API key
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
         * Enables logging, the project importing this module must include an implementation slf4j in their pom.
         *
         * @param level log error level.
         * @return the Builder
         */
        public Builder enableLogging(FlagsmithLoggerLevel level) {
            this.client.logger.setLogger(LoggerFactory.getLogger(FlagsmithClient.class), level);
            return this;
        }

        /**
         * Enables logging, the project importing this module must include an implementation slf4j in their pom.
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
                        .baseURI(apiUrl)
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
         * If no other cache configuration is set, the Caffeine defaults will be used, i.e. no limit
         *
         * @param cacheConfig an FlagsmithCacheConfig.
         * @return the Builder
         */
        public Builder withCache(FlagsmithCacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        public FlagsmithClient build() {
            FlagsmithEndpoints flagsmithEndpoints = new FlagsmithEndpoints(this.configuration, this.customHeaders, client.logger, apiKey);
            if (cacheConfig != null) {
                this.client.flagsmithSDK = new FlagsmithCachedEndpoints(cacheConfig.cache, flagsmithEndpoints);
            } else {
                this.client.flagsmithSDK = flagsmithEndpoints;
            }
            return this.client;
        }
    }
}
