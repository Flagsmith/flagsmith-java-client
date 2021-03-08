package com.flagsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class FlagsmithEndpoints {

  private final FlagsmithConfig defaultConfig;
  private final FlagsmithLogger logger;
  private final HashMap<String, String> customHeaders;
  private static final String AUTH_HEADER = "X-Environment-Key";
  private static final String ACCEPT_HEADER = "Accept";
  // an api key per environment
  private final String apiKey;

  public FlagsmithEndpoints(final FlagsmithConfig defaultConfig,
                            final HashMap<String, String> customHeaders,
                            final FlagsmithLogger logger,
                            final String apiKey) {
    this.defaultConfig = defaultConfig;
    this.customHeaders = customHeaders;
    this.logger = logger;
    this.apiKey = apiKey;
  }

  public List<Flag> getFeatureFlags(FeatureUser user, boolean doThrow) {
    HttpUrl.Builder urlBuilder;
    if (user == null) {
      urlBuilder = defaultConfig.flagsURI.newBuilder()
          .addEncodedQueryParameter("page", "1");
    } else {
      urlBuilder = defaultConfig.flagsURI.newBuilder("")
          .addEncodedPathSegment(user.getIdentifier());
    }

    final Request request = this.newRequestBuilder()
        .url(urlBuilder.build())
        .build();

    Call call = defaultConfig.httpClient.newCall(request);
    List<Flag> featureFlags = new ArrayList<>();
    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ObjectMapper mapper = MapperFactory.getMappper();
        featureFlags = Arrays.asList(mapper.readValue(response.body().string(),
            Flag[].class));
      } else {
        logger.httpError(request, response, doThrow);
      }
    } catch (IOException io) {
      logger.httpError(request, io, doThrow);
    }
    logger.info("Got feature flags for user = {}, flags = {}", user, featureFlags);
    return featureFlags;
  }

  public FlagsAndTraits getUserFlagsAndTraits(FeatureUser user, boolean doThrow) {
    HttpUrl url = defaultConfig.identitiesURI.newBuilder("")
        .addEncodedQueryParameter("identifier", user.getIdentifier())
        .build();

    final Request request = this.newRequestBuilder()
        .url(url)
        .build();

    Call call = defaultConfig.httpClient.newCall(request);

    FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
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
    logger.info("Got feature flags & traits for user = {}, flagsAndTraits = {}", user, flagsAndTraits);
    return flagsAndTraits;
  }

  public List<Trait> identifyUserWithTraits(FeatureUser user, List<Trait> traits, boolean doThrow) {
    // we are using identities endpoint to create bulk user Trait
    HttpUrl url = defaultConfig.identitiesURI;

    if (user == null || (user.getIdentifier() == null || user.getIdentifier().length() < 1)) {
      throw new IllegalArgumentException("Missing user Identifier");
    }

    IdentityTraits identityTraits = new IdentityTraits();
    identityTraits.setIdentifier(user.getIdentifier());
    if (traits != null) {
      identityTraits.setTraits(traits);
    }

    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, identityTraits.toString());

    final Request request = this.newRequestBuilder()
        .post(body)
        .url(url)
        .build();

    List<Trait> traitsData = new ArrayList<>();
    Call call = defaultConfig.httpClient.newCall(request);
    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ObjectMapper mapper = MapperFactory.getMappper();
        FlagsAndTraits flagsAndTraits = mapper.readValue(response.body().string(), FlagsAndTraits.class);

        traitsData = flagsAndTraits.getTraits();
      } else {
        logger.httpError(request, response, doThrow);
      }
    } catch (IOException io) {
      logger.httpError(request, io, doThrow);
    }
    logger.info("Got traits for user = {}, traits = {}", user, traitsData);
    return traitsData;
  }

  public Trait postUserTraits(FeatureUser user, Trait toUpdate, boolean doThrow) {
    HttpUrl url = defaultConfig.traitsURI;
    toUpdate.setIdentity(user);

    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, toUpdate.toString());

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
    logger.info("Updated trait for user = {}, new trait = {}, updated trait = {}", user, toUpdate, trait);
    return trait;
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
}
