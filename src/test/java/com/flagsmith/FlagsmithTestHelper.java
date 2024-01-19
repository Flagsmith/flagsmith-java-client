package com.flagsmith;

import static com.flagsmith.IntegrationTests.BACKEND_PORT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    final int projectId = createProject(projectName, IntegrationTests.TestData.organisationId);

    final Map<String, Object> environment = createEnvironment(environmentName, projectId);
    final String environmentApiKey = (String) environment.get("api_key");

    FlagsmithClient.Builder clientBuilder = FlagsmithClient.newBuilder()
        .withApiUrl(String.format("http://localhost:%d/api/v1/",
            IntegrationTests.TestData.backend.getMappedPort(BACKEND_PORT)))
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

  public static IdentityModel identityOverride() {
    final FeatureModel overriddenFeature = new FeatureModel();
    overriddenFeature.setId(1);
    overriddenFeature.setName("some_feature");
    overriddenFeature.setType("STANDARD");

    final FeatureStateModel overriddenFeatureState = new FeatureStateModel();
    overriddenFeatureState.setFeature(overriddenFeature);
    overriddenFeatureState.setFeaturestateUuid("d5d0767b-6287-4bb4-9d53-8b87e5458642");
    overriddenFeatureState.setValue("overridden-value");
    overriddenFeatureState.setEnabled(true);
    overriddenFeatureState.setMultivariateFeatureStateValues(new ArrayList<>());

    List<FeatureStateModel> identityFeatures = new ArrayList<>();
    identityFeatures.add(overriddenFeatureState);

    final IdentityModel identity = new IdentityModel();
    identity.setIdentifier("overridden-identity");
    identity.setIdentityUuid("65bc5ac6-5859-4cfe-97e6-d5ec2e80c1fb");
    identity.setCompositeKey("B62qaMZNwfiqT76p38ggrQ_identity_overridden_identity");
    identity.setEnvironmentApiKey("B62qaMZNwfiqT76p38ggrQ");
    identity.setIdentityFeatures(identityFeatures);
    return identity;
  }

  public static EnvironmentModel environmentModel() {
    String environment = "{\n" +
    "  \"api_key\": \"B62qaMZNwfiqT76p38ggrQ\",\n" +
    "  \"project\": {\n" +
    "    \"name\": \"Test project\",\n" +
    "    \"organisation\": {\n" +
    "      \"feature_analytics\": false,\n" +
    "      \"name\": \"Test Org\",\n" +
    "      \"id\": 1,\n" +
    "      \"persist_trait_data\": true,\n" +
    "      \"stop_serving_flags\": false\n" +
    "    },\n" +
    "    \"id\": 1,\n" +
    "    \"hide_disabled_flags\": false,\n" +
    "    \"segments\": [\n" +
    "      {\n" +
    "        \"id\": 1,\n" +
    "        \"name\": \"Test segment\",\n" +
    "        \"rules\": [\n" +
    "          {\n" +
    "            \"type\": \"ALL\",\n" +
    "            \"rules\": [\n" +
    "              {\n" +
    "                \"type\": \"ALL\",\n" +
    "                \"rules\": [],\n" +
    "                \"conditions\": [\n" +
    "                  {\n" +
    "                    \"operator\": \"EQUAL\",\n" +
    "                    \"property_\": \"foo\",\n" +
    "                    \"value\": \"bar\"\n" +
    "                  }\n" +
    "                ]\n" +
    "              }\n" +
    "            ]\n" +
    "          }\n" +
    "        ]\n" +
    "      }\n" +
    "    ]\n" +
    "  },\n" +
    "  \"segment_overrides\": [],\n" +
    "  \"id\": 1,\n" +
    "  \"feature_states\": [\n" +
    "    {\n" +
    "      \"multivariate_feature_state_values\": [],\n" +
    "      \"feature_state_value\": \"some-value\",\n" +
    "      \"id\": 1,\n" +
    "      \"featurestate_uuid\": \"40eb539d-3713-4720-bbd4-829dbef10d51\",\n" +
    "      \"feature\": {\n" +
    "        \"name\": \"some_feature\",\n" +
    "        \"type\": \"STANDARD\",\n" +
    "        \"id\": 1\n" +
    "      },\n" +
    "      \"segment_id\": null,\n" +
    "      \"enabled\": true\n" +
    "    }\n" +
    "  ],\n" +
    "  \"identity_overrides\": [\n" +
    "    {\n" +
    "      \"identity_uuid\": \"65bc5ac6-5859-4cfe-97e6-d5ec2e80c1fb\",\n" +
    "      \"identifier\": \"overridden-identity\",\n" +
    "      \"composite_key\": \"B62qaMZNwfiqT76p38ggrQ_identity_overridden_identity\",\n" +
    "      \"identity_features\": [\n" +
    "        {\n" +
    "          \"feature_state_value\": \"overridden-value\",\n" +
    "          \"multivariate_feature_state_values\": [],\n" +
    "          \"featurestate_uuid\": \"d5d0767b-6287-4bb4-9d53-8b87e5458642\",\n" +
    "          \"feature\": {\n" +
    "            \"name\": \"some_feature\",\n" +
    "            \"type\": \"STANDARD\",\n" +
    "            \"id\": 1\n" +
    "          },\n" +
    "          \"enabled\": true\n" +
    "        }\n" +
    "      ],\n" +
    "      \"identity_traits\": [],\n" +
    "      \"environment_api_key\": \"B62qaMZNwfiqT76p38ggrQ\"\n" +
    "    }\n" +
    "  ]\n" +
    "}";

    try {
      return EnvironmentModel.load(MapperFactory.getMapper().readTree(environment), EnvironmentModel.class);
    } catch (JsonProcessingException e) {
      // environment model json
    }

    return null;
  }

  public static List<FeatureStateModel> getFlags() {
    String featureJson = "[\n" +
        "    {\n" +
        "        \"id\": 1,\n" +
        "        \"feature\": {\n" +
        "            \"id\": 1,\n" +
        "            \"name\": \"some_feature\",\n" +
        "            \"created_date\": \"2019-08-27T14:53:45.698555Z\",\n" +
        "            \"initial_value\": null,\n" +
        "            \"description\": null,\n" +
        "            \"default_enabled\": false,\n" +
        "            \"type\": \"STANDARD\",\n" +
        "            \"project\": 1\n" +
        "        },\n" +
        "        \"feature_state_value\": \"some-value\",\n" +
        "        \"enabled\": true,\n" +
        "        \"environment\": 1,\n" +
        "        \"identity\": null,\n" +
        "        \"feature_segment\": null\n" +
        "    }\n" +
        "]";

    try {
      return MapperFactory.getMapper().readValue(
          featureJson,
          new TypeReference<List<FeatureStateModel>>() {}
      );
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      // environment model json
    }

    return null;
  }

  public static String getIdentitiesFlags() {
    String featureJson = "{\n" +
        "    \"traits\": [\n" +
        "        {\n" +
        "            \"id\": 1,\n" +
        "            \"trait_key\": \"some_trait\",\n" +
        "            \"trait_value\": \"some_value\"\n" +
        "        }\n" +
        "    ],\n" +
        "    \"flags\": [\n" +
        "        {\n" +
        "            \"id\": 1,\n" +
        "            \"feature\": {\n" +
        "                \"id\": 1,\n" +
        "                \"name\": \"some_feature\",\n" +
        "                \"created_date\": \"2019-08-27T14:53:45.698555Z\",\n" +
        "                \"initial_value\": null,\n" +
        "                \"description\": null,\n" +
        "                \"default_enabled\": false,\n" +
        "                \"type\": \"STANDARD\",\n" +
        "                \"project\": 1\n" +
        "            },\n" +
        "            \"feature_state_value\": \"some-value\",\n" +
        "            \"enabled\": true,\n" +
        "            \"environment\": 1,\n" +
        "            \"identity\": null,\n" +
        "            \"feature_segment\": null\n" +
        "        }\n" +
        "    ]\n" +
        "}";

    return featureJson;
  }

  public static <T> Future<T> futurableReturn(T response) {
    CompletableFuture<T> promise = new CompletableFuture<>();
    promise.complete(response);
    return promise;
  }

  public static JsonNode getIdentityRequest(String identifier, List<TraitModel> traits) {
    final ObjectNode flagsAndTraits = MapperFactory.getMapper().createObjectNode();
    flagsAndTraits.putPOJO("identifier", identifier);
    flagsAndTraits.putPOJO("traits", traits != null ? traits : new ArrayList<>());
    return flagsAndTraits;
  }

  public static JsonNode getFlagsAndTraitsResponse(List<FeatureStateModel> flags, List<TraitModel> traits) {
    final ObjectNode flagsAndTraits = MapperFactory.getMapper().createObjectNode();
    flagsAndTraits.putPOJO("flags", flags != null ? flags : new ArrayList<>());
    flagsAndTraits.putPOJO("traits", traits != null ? traits : new ArrayList<>());
    return flagsAndTraits;
  }

  public static Headers defaultHeaders() {
    return new Headers(
        new Header("Content-type", "application/json"),
        new Header("Authorization", "Token " + IntegrationTests.TestData.token));
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
