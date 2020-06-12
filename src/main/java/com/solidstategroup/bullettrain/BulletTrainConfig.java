package com.solidstategroup.bullettrain;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * A default configuration for the BulletTrain client SDK.
 *
 * Created by Pavlo Maksymchuk.
 */
public final class BulletTrainConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_WRITE_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final HttpUrl DEFAULT_BASE_URI = HttpUrl.parse("https://api.bullet-train.io/api/v1/");
    private final HttpUrl baseURI;
    final HttpUrl flagsURI;
    final HttpUrl identitiesURI;
    final HttpUrl traitsURI;

    final OkHttpClient httpClient;

    protected BulletTrainConfig(Builder builder) {
        this.baseURI = builder.baseURI;
        this.flagsURI = this.baseURI.newBuilder("flags/").build();
        this.identitiesURI = this.baseURI.newBuilder("identities/").build();
        this.traitsURI = this.baseURI.newBuilder("traits/").build();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(builder.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(builder.writeTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(builder.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    public static BulletTrainConfig.Builder newBuilder() {
        return new BulletTrainConfig.Builder();
    }

    public static class Builder {
        //private BulletTrainConfig config;
        private HttpUrl baseURI = DEFAULT_BASE_URI;
        private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private int writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
        private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;

        private Builder() {
        }

        /**
         * Set the base URL for BulletTrain API, overriding default one.
         *
         * @param baseURI the new base URI for the API.
         * @return the Builder
         */
        public Builder baseURI(String baseURI) {
            if (baseURI != null) {
                this.baseURI = HttpUrl.parse(baseURI);
            }
            return this;
        }

        /**
         * Override default connection timeout for client connection.
         *
         * @param connectTimeoutMillis the connect timeout in milliseconds
         * @return the Builder
         */
        public Builder connectTimeout(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return this;
        }

        /**
         * Override default write timeout for client connection.
         *
         * @param writeTimeoutMillis the write timeout in milliseconds
         * @return the Builder
         */
        public Builder writeTimeout(int writeTimeoutMillis) {
            this.writeTimeoutMillis = writeTimeoutMillis;
            return this;
        }

        /**
         * Override default read timeout for client connection.
         *
         * @param readTimeoutMillis the read timeout in milliseconds
         * @return the Builder
         */
        public Builder readTimeout(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
            return this;
        }

        public BulletTrainConfig build() {
            return new BulletTrainConfig(this);
        }
    }
}
