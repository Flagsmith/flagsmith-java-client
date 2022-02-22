package com.flagsmith;

import static com.flagsmith.IntegrationSuiteTest.BACKEND_PORT;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flag;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class FlagsmithTestHelper {

  public static ProjectEnvironment createProjectEnvironment(String projectName,
      String environmentName) {
    return createProjectEnvironment(projectName, environmentName, false);
  }

  public static ProjectEnvironment createProjectEnvironment(String projectName,
      String environmentName, boolean cached) {
    final int projectId = createProject(projectName, IntegrationSuiteTest.TestData.organisationId);

    final Map<String, Object> environment = createEnvironment(environmentName, projectId);
    final String environmentApiKey = (String) environment.get("api_key");

    FlagsmithClient.Builder clientBuilder = FlagsmithClient.newBuilder()
        .withApiUrl(String.format("http://localhost:%d/api/v1/",
            IntegrationSuiteTest.TestData.backend.getMappedPort(BACKEND_PORT)))
        .setApiKey(environmentApiKey);
    if (cached) {
      clientBuilder.withCache(FlagsmithCacheConfig.newBuilder()
          .maxSize(2).enableEnvLevelCaching("check")
          .build());
    }
    final FlagsmithClient client = clientBuilder.build();
    return new ProjectEnvironment(environmentApiKey, projectId, client);
  }

  public static int createFeature(Feature feature) {
    final ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder()
        .put("name", feature.name)
        .put("type", feature.type)
        .put("project", feature.projectId);
    if (feature.description != null) {
      builder.put("description", feature.description);
    }

    if (feature instanceof FlagFeature) {
      builder.put("default_enabled", ((FlagFeature) feature).enabled);
    } else if (feature instanceof ConfigFeature) {
      builder.put("initial_value", ((ConfigFeature) feature).value);
    } else {
      throw new IllegalStateException("Unsupported feature " + feature);
    }

    return RestAssured.given()
        .body(builder.build())
        .headers(defaultHeaders())
        .post("/api/v1/projects/{projectPk}/features/", feature.projectId)
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getInt("id");
  }

  public static int createUserIdentity(String userIdentity, String environmentApiKey) {
    return RestAssured.given()
        .body(ImmutableMap.of(
            "identifier", userIdentity,
            "environment", environmentApiKey
        ))
        .headers(defaultHeaders())
        .post("/api/v1/environments/{apiKey}/identities/", environmentApiKey)
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getInt("id");
  }

  public static Map<String, Object> createEnvironment(String name, int projectId) {
    return RestAssured.given()
        .body(ImmutableMap.of(
            "name", name,
            "project", projectId
        ))
        .headers(defaultHeaders())
        .post("/api/v1/environments/")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getJsonObject("$");
  }

  public static void switchFlag(int featureId, boolean enabled, String apiKey) {
    final List<Map<String, Object>> features = RestAssured.given()
        .headers(defaultHeaders())
        .get("/api/v1/environments/{apiKey}/featurestates/?feature={featureId}",
            apiKey, featureId)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList("results");
    if (features.isEmpty()) {
      RestAssured.given()
          .body(ImmutableMap.of(
              "enabled", enabled,
              "feature", featureId
          ))
          .headers(defaultHeaders())
          .post("/api/v1/environments/{apiKey}/featurestates/", apiKey)
          .then()
          .statusCode(201);
    } else {
      final List<Integer> featureStateIds = features.stream()
          .map(jsonMap -> jsonMap.get("id"))
          .filter(Integer.class::isInstance)
          .map(Integer.class::cast)
          .collect(Collectors.toList());
      if (featureStateIds.size() == 1) {
        final int featureStateId = featureStateIds.get(0);
        RestAssured.given()
            .body(ImmutableMap.of(
                "enabled", enabled,
                "feature", featureId
            ))
            .headers(defaultHeaders())
            .put("/api/v1/environments/{apiKey}/featurestates/{featureStateId}/",
                apiKey, featureStateId)
            .then()
            .statusCode(200);
      } else {
        throw new IllegalStateException(
            "Unable to decide which feature-state to update: " + features);
      }
    }
  }

  public static void switchFlagForUser(int featureId, int userIdentityId, boolean enabled,
      String apiKey) {
    final List<Map<String, Object>> features = RestAssured.given()
        .headers(defaultHeaders())
        .get(
            "/api/v1/environments/{apiKey}/identities/{identityId}/featurestates/?feature={featureId}",
            apiKey, userIdentityId, featureId)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList("results");
    if (features.isEmpty()) {
      RestAssured.given()
          .body(ImmutableMap.of(
              "enabled", enabled,
              "feature", featureId
          ))
          .headers(defaultHeaders())
          .post("/api/v1/environments/{apiKey}/identities/{identityId}/featurestates/", apiKey,
              userIdentityId)
          .then()
          .statusCode(201);
    } else {
      final List<Integer> featureStateIds = features.stream()
          .filter(jsonMap -> {
            final Map<String, Object> identityMap = (Map<String, Object>) jsonMap.get("identity");
            return Integer.valueOf(userIdentityId).equals(identityMap.get("id"));
          })
          .map(jsonMap -> jsonMap.get("id"))
          .filter(Integer.class::isInstance)
          .map(Integer.class::cast)
          .collect(Collectors.toList());
      if (featureStateIds.size() == 1) {
        final int featureStateId = featureStateIds.get(0);
        RestAssured.given()
            .body(ImmutableMap.of(
                "enabled", enabled,
                "feature", featureId
            ))
            .headers(defaultHeaders())
            .put(
                "/api/v1/environments/{apiKey}/identities/{identityId}/featurestates/{featureStateId}/",
                apiKey, userIdentityId, featureStateId)
            .then()
            .statusCode(200);
      } else {
        throw new IllegalStateException(
            "Unable to decide which feature-state to update: " + features);
      }
    }
  }

  public static void assignTraitToUserIdentity(String userIdentifier, String traitKey,
      Object traitValue, String apiKey) {
    RestAssured.given()
        .body(ImmutableMap.of(
            "identity", ImmutableMap.of("identifier", userIdentifier),
            "trait_key", traitKey,
            "trait_value", traitValue
        ))
        .headers(defaultHeaders())
        .header("x-environment-key", apiKey)
        .post("/api/v1/traits/")
        .then()
        .statusCode(200);
  }

  public static int createProject(String name, int organisationId) {
    return RestAssured.given()
        .body(ImmutableMap.of(
            "name", name,
            "organisation", organisationId
        ))
        .headers(defaultHeaders())
        .post("/api/v1/projects/")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getInt("id");
  }

  public static BaseFlag flag(
      String name, String description, String type, boolean enabled, String value
  ) {
    final FeatureModel feature = new FeatureModel();
    feature.setName(name);
    feature.setType(type);

    final FeatureStateModel result = new FeatureStateModel();
    result.setFeature(feature);
    result.setEnabled(enabled);
    result.setValue(value);
    return Flag.fromFeatureStateModel(result, null);
  }

  public static BaseFlag flag(String name, String description, boolean enabled) {
    return flag(name, description, "FLAG", enabled, null);
  }

  public static BaseFlag config(String name, String description, String value) {
    return flag(name, description, "CONFIG", true, value);
  }

  public static TraitModel trait(String userIdentifier, String key, String value) {
    final TraitModel result = new TraitModel();
    result.setTraitKey(key);
    result.setTraitValue(value);
    return result;
  }

  public static IdentityModel featureUser(String identifier) {
    final IdentityModel user = new IdentityModel();
    user.setIdentifier(identifier);
    return user;
  }

  public static <T> Future<T> futurableReturn(T response) {
    CompletableFuture<T> promise = new CompletableFuture<>();
    promise.complete(response);
    return promise;
  }

  public static Headers defaultHeaders() {
    return new Headers(
        new Header("Content-type", "application/json"),
        new Header("Authorization", "Token " + IntegrationSuiteTest.TestData.token));
  }

  public static abstract class Feature {

    final String type;
    final String name;
    final String description;
    final int projectId;

    protected Feature(String type, String name, String description, int projectId) {
      this.type = type;
      this.name = name;
      this.description = description;
      this.projectId = projectId;
    }
  }

  public static class FlagFeature extends Feature {

    final boolean enabled;

    protected FlagFeature(String name, String description, int projectId, boolean enabled) {
      super("FLAG", name, description, projectId);
      this.enabled = enabled;
    }
  }

  public static class ConfigFeature extends Feature {

    final String value;

    protected ConfigFeature(String name, String description, int projectId, String value) {
      super("CONFIG", name, description, projectId);
      this.value = value;
    }
  }

  public static class ProjectEnvironment {

    final String apiKey;
    final int projectId;
    final FlagsmithClient client;

    private ProjectEnvironment(String apiKey, int projectId, FlagsmithClient client) {
      this.apiKey = apiKey;
      this.projectId = projectId;
      this.client = client;
    }
  }
}
