package com.ssg.bullettrain;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A client for Bullet Train API.
 */
public class BulletTrainClient {
    private static final String AUTH_HEADER = "X-Environment-Key";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String ACCEPT_HEADER = "Accept";
    // an api key per environment
    private String apiKey;

    private final HttpUrl DEFAULT_BASE_URI = HttpUrl.parse("https://bullet-train-api-dev.dokku1.solidstategroup.com/api/v1/");
    private final OkHttpClient httpClient;

    private BulletTrainClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }


    /**
     * Get a list of existing Features for the given environment
     *
     * @return
     */
    public List<FeatureFlag> getFeatureFlags() {
        return getFeatureFlags(null);
    }

    /**
     * Get a list of existing Features for the given environment and user
     *
     * @param user
     * @return
     */
    public List<FeatureFlag> getFeatureFlags(User user) {
        HttpUrl url;
        if (user == null) {
            url = DEFAULT_BASE_URI.newBuilder("flags/")
                    .addEncodedQueryParameter("page", "1")
                    .build();
        } else {
            url = DEFAULT_BASE_URI.newBuilder("flags/")
                    .addEncodedPathSegment(user.getIdentifier())
                    .build();

        }

        Request request = new Request.Builder()
                .header(AUTH_HEADER, apiKey)
                .addHeader(ACCEPT_HEADER, "application/json")
                .url(url)
                .build();

        Call call = httpClient.newCall(request);

        try (Response response = call.execute()) {
            if (response.isSuccessful()) {
                ObjectMapper mapper = MapperFactory.getMappper();
                List<FeatureFlag> featureFlags = Arrays.asList(mapper.readValue(response.body().string(),
                        FeatureFlag[].class));

                return featureFlags;
            }
        } catch (IOException io) {
            return null;
        }
        return null;
    }


    /**
     * Check if FeatureFlag exist and is enabled
     *
     * @param featureId an identifier for the feature
     * @return true if feature flag exist and enabled, false otherwise
     */
    public boolean hasFeatureFlag(String featureId) {
        List<FeatureFlag> featureFlags = getFeatureFlags();
        for (FeatureFlag flag : featureFlags) {
            if (flag.getId().equals(featureId) && flag.isEnabled()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if FeatureFlag exist and is enabled for given user
     *
     * @param featureId an identifier for the feature
     * @return true if feature flag exist and enabled, false otherwise
     */
    public boolean hasFeatureFlag(String featureId, User user) {
        List<FeatureFlag> featureFlags = getFeatureFlags(user);
        for (FeatureFlag flag : featureFlags) {
            if (flag.getId().equals(featureId) && flag.isEnabled()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get FeatureFlag value for given feature id.
     *
     * @param featureId an identifier for the feature
     * @return a value for the FeatureFlag or null if feature does not exist or not enabled
     */
    public String getFeatureFlagValue(String featureId) {
        List<FeatureFlag> featureFlags = getFeatureFlags();
        for (FeatureFlag flag : featureFlags) {
            if (flag.getId().equals(featureId) && flag.isEnabled()) {
                return flag.getValue();
            }
        }

        return null;
    }

    /**
     * Get FeatureFlag value for given feature id and user
     *
     * @param featureId
     * @param user      a user in context
     * @return
     */
    public String getFeatureFlagValue(String featureId, User user) {
        List<FeatureFlag> featureFlags = getFeatureFlags(user);
        for (FeatureFlag flag : featureFlags) {
            if (flag.getId().equals(featureId) && flag.isEnabled()) {
                return flag.getValue();
            }
        }

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
         * @param apiKey
         * @return
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


            return client;
        }
    }
}
