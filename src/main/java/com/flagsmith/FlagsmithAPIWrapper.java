package com.flagsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class FlagsmithAPIWrapper implements FlagsmithSDK {

  private static final String AUTH_HEADER = "X-Environment-Key";
  private static final String ACCEPT_HEADER = "Accept";
  private final FlagsmithLogger logger;
  private final FlagsmithConfig defaultConfig;
  private final HashMap<String, String> customHeaders;
  // an api key per environment
  private final String apiKey;

  public FlagsmithAPIWrapper(final FlagsmithConfig defaultConfig,
      final HashMap<String, String> customHeaders,
      final FlagsmithLogger logger,
      final String apiKey) {
    this.defaultConfig = defaultConfig;
    this.customHeaders = customHeaders;
    this.logger = logger;
    this.apiKey = apiKey;
  }

  @Override
  public FlagsAndTraits getFeatureFlags(FeatureUser user, boolean doThrow) {
    HttpUrl.Builder urlBuilder;
    if (user == null) {
      urlBuilder = defaultConfig.flagsUri.newBuilder()
          .addEncodedQueryParameter("page", "1");
    } else {
      return getUserFlagsAndTraits(user, doThrow);
    }

    final Request request = this.newRequestBuilder()
        .url(urlBuilder.build())
        .build();

    Call call = defaultConfig.httpClient.newCall(request);
    FlagsAndTraits flagsAndTraits = newFlagsAndTraits();
    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ObjectMapper mapper = MapperFactory.getMappper();
        List<Flag> featureFlags = Arrays.asList(mapper.readValue(response.body().string(),
            Flag[].class));
        flagsAndTraits.setFlags(featureFlags);
      } else {
        logger.httpError(request, response, doThrow);
      }
    } catch (IOException io) {
      logger.httpError(request, io, doThrow);
    }
    logger.info("Got feature flags for user = {}, flags = {}", user, flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
    assertValidUser(user);

    HttpUrl url = defaultConfig.identitiesUri.newBuilder("")
        .addEncodedQueryParameter("identifier", user.getIdentifier())
        .build();

    final Request request = this.newRequestBuilder()
        .url(url)
        .build();

    Call call = defaultConfig.httpClient.newCall(request);

    FlagsAndTraits flagsAndTraits = newFlagsAndTraits();
    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ObjectMapper mapper = MapperFactory.getMappper();
        flagsAndTraits = mapper.readValue(response.body().string(), FlagsAndTraits.class);
      } else {
        logger.httpError(request, response, doThrow);
      }
    } catch (IOException io) {
      logger.httpError(request, io, doThrow);
    }
    logger.info("Got feature flags & traits for user = {}, flagsAndTraits = {}", user,
        flagsAndTraits);
    return flagsAndTraits;
  }

  @Override
  public Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow) {
    HttpUrl url = defaultConfig.traitsUri;
    toUpdate.setIdentity(user);

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(json, toUpdate.toString());

    Request request = this.newRequestBuilder()
        .post(body)
        .url(url)
        .build();

    Trait trait = null;
    Call call = defaultConfig.httpClient.newCall(request);
    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ObjectMapper mapper = MapperFactory.getMappper();
        trait = mapper.readValue(response.body().string(), Trait.class);
      } else {
        logger.httpError(request, response, doThrow);
      }
    } catch (IOException io) {
      logger.httpError(request, io, doThrow);
    }
    logger.info("Updated trait for user = {}, new trait = {}, updated trait = {}",
        user, toUpdate, trait);
    return trait;
  }

  @Override
  public FlagsAndTraits identifyUserWithTraits(
      FeatureUser user, List<Trait> traits, boolean doThrow) {
    assertValidUser(user);

    // we are using identities endpoint to create bulk user Trait
    HttpUrl url = defaultConfig.identitiesUri;

    IdentityTraits identityTraits = new IdentityTraits();
    identityTraits.setIdentifier(user.getIdentifier());
    if (traits != null) {
      identityTraits.setTraits(traits);
    }

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(json, identityTraits.toString());

    final Request request = this.newRequestBuilder()
        .post(body)
        .url(url)
        .build();

    FlagsAndTraits flagsAndTraits = newFlagsAndTraits();
    Call call = defaultConfig.httpClient.newCall(request);
    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ObjectMapper mapper = MapperFactory.getMappper();
        flagsAndTraits = mapper.readValue(response.body().string(), FlagsAndTraits.class);
      } else {
        logger.httpError(request, response, doThrow);
      }
    } catch (IOException io) {
      logger.httpError(request, io, doThrow);
    }
    logger.info("Got traits for user = {}, traits = {}", user, flagsAndTraits.getTraits());
    return flagsAndTraits;
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

  private FlagsAndTraits newFlagsAndTraits() {
    FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    flagsAndTraits.setFlags(new ArrayList<>());
    flagsAndTraits.setTraits(new ArrayList<>());
    return flagsAndTraits;
  }
}
