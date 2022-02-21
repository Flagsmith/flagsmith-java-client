package com.flagsmith;

import com.fasterxml.jackson.core.type.TypeReference;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.threads.RequestProcessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.Data;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Data
public class FlagsmithApiWrapper implements FlagsmithSdk {

  private static final String AUTH_HEADER = "X-Environment-Key";
  private static final String ACCEPT_HEADER = "Accept";
  private final FlagsmithLogger logger;
  private final FlagsmithConfig defaultConfig;
  private final HashMap<String, String> customHeaders;
  // an api key per environment
  private final String apiKey;
  private final RequestProcessor requestor;

  /**
   * Instantiate with config, custom headers, logger and apikey.
   * @param defaultConfig config object
   * @param customHeaders custom headers list
   * @param logger logger instance
   * @param apiKey api key
   */
  public FlagsmithApiWrapper(final FlagsmithConfig defaultConfig,
      final HashMap<String, String> customHeaders,
      final FlagsmithLogger logger,
      final String apiKey) {
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

  @Override
  public List<FeatureStateModel> getFeatureFlags(String identifier, boolean doThrow) {
    HttpUrl.Builder urlBuilder;
    if (identifier == null) {
      urlBuilder = defaultConfig.getFlagsUri().newBuilder()
          .addEncodedQueryParameter("page", "1");
    } else {
      return getUserFlagsAndTraits(identifier, doThrow).getFlags();
    }

    final Request request = this.newRequestBuilder()
        .url(urlBuilder.build())
        .build();

    Future<List<FeatureStateModel>> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<List<FeatureStateModel>>() {},
        doThrow
    );

    List<FeatureStateModel> featureFlags = null;

    try {
      featureFlags = featureFlagsFuture.get();
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
    }

    featureFlags = enrichWithDefaultFlags(featureFlags);
    logger.info("Got feature flags for user = {}, flags = {}", identifier, featureFlags);
    return featureFlags;
  }

  /**
   * Get Feature Flags from API.
   *
   * @param doThrow - whether throw exception or not
   * @return
   */
  public List<FeatureStateModel> getFeatureFlags(boolean doThrow) {
    HttpUrl.Builder urlBuilder = defaultConfig.getFlagsUri().newBuilder()
        .addEncodedQueryParameter("page", "1");

    final Request request = this.newRequestBuilder()
        .url(urlBuilder.build())
        .build();

    Future<List<FeatureStateModel>> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<List<FeatureStateModel>>() {},
        doThrow
    );

    List<FeatureStateModel> featureFlags = null;

    try {
      featureFlags = featureFlagsFuture.get();
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
    }

    logger.info("Got feature flags for flags = {}", featureFlags);
    return featureFlags;
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(String identifier, boolean doThrow) {
    assertValidUser(identifier);

    HttpUrl url = defaultConfig.getIdentitiesUri().newBuilder("")
        .addEncodedQueryParameter("identifier", identifier)
        .build();

    final Request request = this.newRequestBuilder()
        .url(url)
        .build();

    Future<FlagsAndTraits> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<FlagsAndTraits>() {},
        doThrow
    );

    FlagsAndTraits flagsAndTraits = newFlagsAndTraits();

    try {
      flagsAndTraits = featureFlagsFuture.get();
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
    }

    flagsAndTraits.setFlags(enrichWithDefaultFlags(flagsAndTraits.getFlags()));
    logger.info("Got feature flags & traits for user = {}, flagsAndTraits = {}", identifier,
        flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public TraitModel postUserTraits(String identifier, TraitModel toUpdate, boolean doThrow) {
    HttpUrl url = defaultConfig.getTraitsUri();

    IdentityModel identityModel = new IdentityModel();
    identityModel.setIdentifier(identifier);

    TraitRequest traitRequest = new TraitRequest();
    traitRequest.setIdentity(identityModel);

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(traitRequest.toString(), json);

    Request request = this.newRequestBuilder()
        .post(body)
        .url(url)
        .build();

    Future<TraitRequest> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<TraitRequest>() {},
        doThrow
    );

    TraitRequest trait = null;
    try {
      trait = featureFlagsFuture.get();
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
    }
    logger.info("Updated trait for user = {}, new trait = {}, updated trait = {}",
        identifier, toUpdate, trait);
    return trait;
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(
      String identifier, List<TraitModel> traits, boolean doThrow) {
    assertValidUser(identifier);

    // we are using identities endpoint to create bulk user Trait
    HttpUrl url = defaultConfig.getIdentitiesUri();

    IdentityTraits identityTraits = new IdentityTraits();
    identityTraits.setIdentifier(identifier);

    if (traits != null) {
      identityTraits.setTraits(traits);
    }

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(identityTraits.toString(), json);

    final Request request = this.newRequestBuilder()
        .post(body)
        .url(url)
        .build();

    Future<FlagsAndTraits> featureFlagsFuture = requestor.executeAsync(
        request,
        new TypeReference<FlagsAndTraits>() {},
        doThrow
    );

    FlagsAndTraits flagsAndTraits = newFlagsAndTraits();

    try {
      flagsAndTraits = featureFlagsFuture.get();
    } catch (InterruptedException ie) {
      logger.error("Interrupted on fetching Feature flags.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on fetching Feature flags.", ee);
    }

    flagsAndTraits.setFlags(enrichWithDefaultFlags(flagsAndTraits.getFlags()));
    logger.info("Got flags based on identify for user = {}, flags = {}", identifier, flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public EnvironmentModel getEnvironment() {
    final Request request = this.newGetRequest(defaultConfig.getEnvironmentUri());

    Future<EnvironmentModel> environmentFuture = requestor.executeAsync(request,
        new TypeReference<EnvironmentModel>() {},
        Boolean.FALSE);

    try {
      return environmentFuture.get();
    } catch (InterruptedException ie) {
      logger.error("Environment loading interrupted.", ie);
    } catch (ExecutionException ee) {
      logger.error("Execution failed on Environment loading.", ee);
    }

    return null;
  }

  @Override
  public FlagsmithConfig getConfig() {
    return this.defaultConfig;
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
  public Request newPostRequest(HttpUrl url, RequestBody body) {
    final Request.Builder builder = newRequestBuilder();
    builder.url(url).post(body);

    return builder.build();
  }

  private List<FeatureStateModel> enrichWithDefaultFlags(List<FeatureStateModel> baseFlags) {
    return this.defaultConfig.getFlagsmithFlagDefaults().enrichWithDefaultFlags(baseFlags);
  }

  private FlagsAndTraits newFlagsAndTraits() {
    FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    flagsAndTraits.setFlags(new ArrayList<>());
    flagsAndTraits.setTraits(new ArrayList<>());
    return flagsAndTraits;
  }
}
