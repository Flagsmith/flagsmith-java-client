package com.flagsmith.config;

import com.flagsmith.FlagsmithClient;
import com.flagsmith.FlagsmithFlagDefaults;
import com.flagsmith.interfaces.DefaultFlagHandler;
import com.flagsmith.threads.AnalyticsProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import lombok.Data;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/**
 * A default configuration for the Flagsmith client SDK.
 *
 * <p>Created by Pavlo Maksymchuk.
 */
@Data
public final class FlagsmithConfig {

  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
  private static final int DEFAULT_WRITE_TIMEOUT_MILLIS = 5000;
  private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
  private static final HttpUrl DEFAULT_BASE_URI = HttpUrl
      .parse("https://api.flagsmith.com/api/v1/");
  private final HttpUrl flagsUri;
  private final HttpUrl identitiesUri;
  private final HttpUrl traitsUri;
  private final HttpUrl environmentUri;
  private final OkHttpClient httpClient;
  private final FlagsmithFlagDefaults flagsmithFlagDefaults = new FlagsmithFlagDefaults();
  private final HttpUrl baseUri;

  private final Retry retries;
  private Boolean enableLocalEvaluation = Boolean.FALSE;
  private Integer environmentRefreshIntervalSeconds = 60000;
  private AnalyticsProcessor analyticsProcessor;

  protected FlagsmithConfig(Builder builder) {
    this.baseUri = builder.baseUri;
    this.flagsUri = this.baseUri.newBuilder("flags/").build();
    this.identitiesUri = this.baseUri.newBuilder("identities/").build();
    this.traitsUri = this.baseUri.newBuilder("traits/").build();
    this.environmentUri = this.baseUri.newBuilder("environment-document/").build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
        .connectTimeout(builder.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(builder.writeTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(builder.readTimeoutMillis, TimeUnit.MILLISECONDS);
    if (builder.sslSocketFactory != null && builder.trustManager != null) {
      httpBuilder = httpBuilder.sslSocketFactory(builder.sslSocketFactory, builder.trustManager);
    }
    for (final Interceptor interceptor : builder.interceptors) {
      httpBuilder = httpBuilder.addInterceptor(interceptor);
    }
    this.httpClient = httpBuilder.build();

    this.retries = builder.retries;
    this.enableLocalEvaluation = builder.enableLocalEvaluation;
    this.environmentRefreshIntervalSeconds = builder.environmentRefreshIntervalSeconds;

    if (builder.enableAnalytics) {
      if (builder.analyticsProcessor != null) {
        analyticsProcessor = builder.analyticsProcessor;
      } else {
        analyticsProcessor = new AnalyticsProcessor(httpClient);
      }
    }
  }

  public static FlagsmithConfig.Builder newBuilder() {
    return new FlagsmithConfig.Builder();
  }

  public static class Builder {

    private final List<Interceptor> interceptors = new ArrayList<>();
    private HttpUrl baseUri = DEFAULT_BASE_URI;
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private int writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
    private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
    private Retry retries = new Retry(3);
    private SSLSocketFactory sslSocketFactory;
    private X509TrustManager trustManager;
    private FlagsmithFlagDefaults flagsmithFlagDefaults;
    private AnalyticsProcessor analyticsProcessor;

    private Boolean enableLocalEvaluation = Boolean.FALSE;
    private Integer environmentRefreshIntervalSeconds = 60000;
    private Boolean enableAnalytics = Boolean.FALSE;

    private Builder() {
    }

    /**
     * Set the base URL for Flagsmith API, overriding default one.
     *
     * @param baseUri the new base URI for the API.
     * @return the Builder
     */
    public Builder baseUri(String baseUri) {
      if (baseUri != null) {
        this.baseUri = HttpUrl.parse(baseUri);
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
     * @param trustManager     X509TrustManager trust manager
     * @return the Builder
     */
    public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory,
        X509TrustManager trustManager) {
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

    /**
     * Add retries for HTTP request to the builder.
     * @param retries no of retries for requests
     * @return
     */
    public Builder retries(Retry retries) {
      this.retries = retries;
      return this;
    }

    /**
     * Local evaluation config.
     * @param localEvaluation boolean to enable
     * @return
     */
    public Builder withLocalEvaluation(Boolean localEvaluation) {
      this.enableLocalEvaluation = localEvaluation;
      return this;
    }

    /**
     * set environment refresh rate with polling manager. Only needed when local evaluation is true.
     * @param seconds seconds
     * @return
     */
    public Builder withEnvironmentRefreshIntervalSeconds(Integer seconds) {
      this.environmentRefreshIntervalSeconds = seconds;
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
      enableAnalytics = Boolean.TRUE;
      return this;
    }


    /**
     * Enable Analytics Processor.
     * @param enable boolean to enable
     * @return
     */
    public Builder withEnableAnalytics(Boolean enable) {
      this.enableAnalytics = enable;
      return this;
    }

    public FlagsmithConfig build() {
      return new FlagsmithConfig(this);
    }
  }
}
