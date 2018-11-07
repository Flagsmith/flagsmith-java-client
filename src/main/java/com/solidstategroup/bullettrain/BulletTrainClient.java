package com.solidstategroup.bullettrain;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A client for Bullet Train API.
 */
public class BulletTrainClient {

    private BulletTrainConfig defaultConfig;
    private static final String AUTH_HEADER = "X-Environment-Key";
    private static final String ACCEPT_HEADER = "Accept";
    // an api key per environment
    private String apiKey;

    private BulletTrainClient() {
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
        HttpUrl url;
        if (user == null) {
            url = defaultConfig.flagsURI.newBuilder()
                    .addEncodedQueryParameter("page", "1")
                    .build();
        } else {
            url = defaultConfig.flagsURI.newBuilder("")
                    .addEncodedPathSegment(user.getIdentifier())
                    .build();
        }

        Request request = new Request.Builder()
                .header(AUTH_HEADER, apiKey)
                .addHeader(ACCEPT_HEADER, "application/json")
                .url(url)
                .build();

        Call call = defaultConfig.httpClient.newCall(request);

        try (Response response = call.execute()) {
            if (response.isSuccessful()) {
                ObjectMapper mapper = MapperFactory.getMappper();
                List<Flag> featureFlags = Arrays.asList(mapper.readValue(response.body().string(),
                        Flag[].class));

                return featureFlags;
            }
        } catch (IOException io) {
            return null;
        }
        return null;
    }


    /**
     * Check if Feature flag exist and is enabled
     *
     * @param featureId an identifier for the feature
     * @return true if feature flag exist and enabled, false otherwise
     */
    public boolean hasFeatureFlag(String featureId) {
        List<Flag> featureFlags = getFeatureFlags();
        for (Flag flag : featureFlags) {
            if (flag.getFeature().getName().equals(featureId) && flag.isEnabled()) {
                return true;
            }
        }

        return false;
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
        for (Flag flag : featureFlags) {
            if (flag.getFeature().getName().equals(featureId) && flag.isEnabled()) {
                return true;
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
        for (Flag flag : featureFlags) {
            if (flag.getFeature().getName().equals(featureId)) {
                return flag.getStateValue();
            }
        }

        return null;
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
        for (Flag flag : featureFlags) {
            if (flag.getFeature().getName().equals(featureId)) {
                return flag.getStateValue();
            }
        }

        return null;
    }


    /**
     * Get a list of existing Features and user traits for user identity
     * @return
     */
    public List<?> getFlagsAndTraits(){

        // TODO: implement
        return null;
    }


    public static BulletTrainClient.Builder newBuilder() {
        return new BulletTrainClient.Builder();
    }


    public static class Builder {
        private BulletTrainClient client;

        private Builder() {
            client = new BulletTrainClient();
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
                client.apiKey = apiKey;
                return this;
            }
        }

        public BulletTrainClient build() {
            client.defaultConfig = BulletTrainConfig.newBuilder().build();
            return client;
        }
    }
}
