package com.flagsmith;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A default configuration for the Flagsmith client SDK.
 * <p>
 * Created by Pavlo Maksymchuk.
 */
public final class FlagsmithConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_WRITE_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final HttpUrl DEFAULT_BASE_URI = HttpUrl.parse("https://api.flagsmith.com/api/v1/");
    private final HttpUrl baseURI;
    final HttpUrl flagsURI;
    final HttpUrl identitiesURI;
    final HttpUrl traitsURI;

    final OkHttpClient httpClient;

    protected FlagsmithConfig(Builder builder) {
        this.baseURI = builder.baseURI;
        this.flagsURI = this.baseURI.newBuilder("flags/").build();
        this.identitiesURI = this.baseURI.newBuilder("identities/").build();
        this.traitsURI = this.baseURI.newBuilder("traits/").build();
        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
            .connectTimeout(builder.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(builder.writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(builder.readTimeoutMillis, TimeUnit.MILLISECONDS);
        if (builder.sslSocketFactory != null && builder.trustManager != null) {
            httpBuilder = httpBuilder.sslSocketFactory(builder.sslSocketFactory, builder.trustManager);
        }
        for (final Interceptor interceptor: builder.interceptors) {
            httpBuilder = httpBuilder.addInterceptor(interceptor);
        }
        this.httpClient = httpBuilder.build();
    }

    public static FlagsmithConfig.Builder newBuilder() {
        return new FlagsmithConfig.Builder();
    }

    public static class Builder {
        private HttpUrl baseURI = DEFAULT_BASE_URI;
        private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private int writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
        private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
        private SSLSocketFactory sslSocketFactory;
        private X509TrustManager trustManager;
        private final List<Interceptor> interceptors = new ArrayList<>();

        private Builder() {
        }

        /**
         * Set the base URL for Flagsmith API, overriding default one.
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

        /**
         * Added custom SSL certificate.
         *
         * @param sslSocketFactory SSL factory
         * @param trustManager X509TrustManager trust manager
         * @return the Builder
         */
        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
            this.sslSocketFactory = sslSocketFactory;
            this.trustManager = trustManager;
            return this;
        }

        /**
         * Add a custom HTTP interceptor.
         *
         * @param interceptor the HTTP interceptor
         * @return the Builder
         */
        public Builder addHttpInterceptor(Interceptor interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        public FlagsmithConfig build() {
            return new FlagsmithConfig(this);
        }
    }
}
