package com.flagsmith;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and
 * features ids etc as required.
 */
@Test(groups = "integration")
public class FlagsmithClientTest {
    private static final Logger log = LoggerFactory.getLogger(FlagsmithClientTest.class);

    private static final Network network = Network.newNetwork();
    protected static final int BACKEND_PORT = 8000;

    private static PostgreSQLContainer<?> postgres;

    private static GenericContainer<?> backend;

    private static String token;

    private static int organisationId;

    @BeforeClass
    public static void beforeClass() {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:10.6-alpine"))
                .withNetwork(network)
                .withNetworkAliases("flagsmith-db")
                .withDatabaseName("flagsmith")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("flagsmith-db")));
        postgres.start();

        backend = new GenericContainer<>(DockerImageName.parse("flagsmith/flagsmith-api:v2-5-2"))
                .withNetwork(network)
                .withNetworkAliases("flagsmith-be")
                .withEnv("DJANGO_ALLOWED_HOSTS", "*")
                .withEnv("DATABASE_URL", String.format("postgresql://%s:%s@%s:%d/%s",
                        postgres.getUsername(),
                        postgres.getPassword(),
                        "flagsmith-db",
                        PostgreSQLContainer.POSTGRESQL_PORT,
                        postgres.getDatabaseName()))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("flagsmith-be")))
                .withExposedPorts(BACKEND_PORT)
                .waitingFor(new HttpWaitStrategy().forPath("/health").withHeader("Accept", "application/json"));
        backend.start();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.port = backend.getMappedPort(BACKEND_PORT);

        assertThat(RestAssured.get("/api/v1/users/init/").asString())
                .describedAs("Super user have to be created")
                .isEqualTo("ADMIN USER CREATED");

        token = flagSmithAuthenticate();
        organisationId = createOrganisation("Test Organisation");
    }

    @AfterClass
    public static void afterClass() {
        backend.stop();
        postgres.stop();
        network.close();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_Features_Then_Success",
                "TEST");

        createFeature(new FlagFeature(
                "Flag 1",
                "Description for Flag 1",
                environment.projectId,
                true));
        createFeature(new FlagFeature(
                "Flag 2",
                "Description for Flag 2",
                environment.projectId,
                false));
        createFeature(new ConfigFeature(
                "Config 1",
                "Description for Config 1",
                environment.projectId,
                "xxx"));
        createFeature(new ConfigFeature(
                "Config 2",
                "Description for Config 2",
                environment.projectId,
                "foo"));

        final List<Flag> featureFlags = environment.client.getFeatureFlags();

        assertThat(featureFlags)
                .isNotNull()
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        flag("Flag 1", "Description for Flag 1", true),
                        flag("Flag 2", "Description for Flag 2", false),
                        config("Config 1", "Description for Config 1", "xxx"),
                        config("Config 2", "Description for Config 2", "foo")
                );
    }

    @Test(groups = "integration")
    public void testClient_When_Has_Feature_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Has_Feature_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagFeature(
                "Flag disabled",
                null,
                environment.projectId,
                false));

        switchFlag(featureId, true, environment.apiKey);

        final boolean enabled = environment.client.hasFeatureFlag("Flag disabled");
        assertThat(enabled)
                .describedAs("Disabled by default, but enabled")
                .isTrue();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_For_User_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_Features_For_User_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagFeature(
                "Flag to be enabled for the user",
                null,
                environment.projectId,
                false));
        createFeature(new FlagFeature(
                "Other Flag",
                null,
                environment.projectId,
                false));

        createUserIdentity("first-user", environment.apiKey);
        final int secondUserId = createUserIdentity("second-user", environment.apiKey);

        switchFlagForUser(featureId, secondUserId, true, environment.apiKey);

        final FeatureUser user = featureUser("second-user");

        final List<Flag> listForUserEnabled = environment.client.getFeatureFlags(user);
        assertThat(listForUserEnabled)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        flag("Flag to be enabled for the user", null, true),
                        flag("Other Flag", null, false)
                );

        final List<Flag> listWithoutUser = environment.client.getFeatureFlags();
        assertThat(listWithoutUser)
                .hasSize(2)
                .allSatisfy(flag -> assertThat(flag.isEnabled()).isFalse());

        switchFlagForUser(featureId, secondUserId, false, environment.apiKey);

        final List<Flag> listForUserDisabled = environment.client.getFeatureFlags(user);
        assertThat(listForUserDisabled)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        flag("Flag to be enabled for the user", null, false),
                        flag("Other Flag", null, false)
                );
    }


    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_Then_Success",
                "TEST");

        assignTraitToUserIdentity("user-with-traits", "foo1", "bar", environment.apiKey);
        assignTraitToUserIdentity("user-with-traits", "foo2", 123, environment.apiKey);
        assignTraitToUserIdentity("user-with-traits", "foo3", 3.14, environment.apiKey);
        assignTraitToUserIdentity("user-with-traits", "foo4", false, environment.apiKey);

        final FeatureUser user = featureUser("user-with-traits");

        final List<Trait> traits = environment.client.getTraits(user);
        assertThat(traits)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        trait(null, "foo1", "bar"),
                        trait(null, "foo2", "123"),
                        trait(null, "foo3", "3.14"),
                        trait(null, "foo4", "false")
                );
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Keys_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_For_Keys_Then_Success",
                "TEST");

        assignTraitToUserIdentity("user-with-key-traits", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("user-with-key-traits", "foo2", "yyy", environment.apiKey);
        assignTraitToUserIdentity("user-with-key-traits", "foo3", "zzz", environment.apiKey);

        final FeatureUser user = featureUser("user-with-key-traits");

        final List<Trait> traits = environment.client.getTraits(user, "foo2", "foo3", "foo-missing");
        assertThat(traits)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        trait(null, "foo2", "yyy"),
                        trait(null, "foo3", "zzz")
                );
        assertThat(traits)
                .extracting(Trait::getKey)
                .doesNotContain("foo-missing");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Empty() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Empty",
                "TEST");

        assignTraitToUserIdentity("mr-user", "foo", "bar", environment.apiKey);

        final FeatureUser user = featureUser("invalid-user");

        final List<Trait> traits = environment.client.getTraits(user);
        assertThat(traits)
                .isEmpty();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagFeature(
                "Flag to be enabled for the user",
                null,
                environment.projectId,
                false));
        createFeature(new FlagFeature(
                "Other Flag",
                null,
                environment.projectId,
                false));

        createUserIdentity("mr-user-1", environment.apiKey);
        final int userId = createUserIdentity("mr-user-2", environment.apiKey);

        switchFlagForUser(featureId, userId, true, environment.apiKey);

        assignTraitToUserIdentity("mr-user-2", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("mr-user-999", "foo2", "yyy", environment.apiKey);

        final FeatureUser user = featureUser("mr-user-2");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        assertThat(flagsAndTraits)
                .isNotNull();
        assertThat(flagsAndTraits.getFlags())
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        flag("Flag to be enabled for the user", null, true),
                        flag("Other Flag", null, false)
                );
        assertThat(flagsAndTraits.getTraits())
                .hasSize(1)
                .contains(trait(null, "foo1", "xxx"));
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Empty() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Empty",
                "TEST");

        createFeature(new FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));

        assignTraitToUserIdentity("mr-user", "foo", "bar", environment.apiKey);

        final FeatureUser user = featureUser("different-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        assertThat(flagsAndTraits)
                .isNotNull();
        assertThat(flagsAndTraits.getFlags())
                .hasSize(1)
                .contains(flag("The Flag", null, false));
        assertThat(flagsAndTraits.getTraits())
                .isEmpty();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        createFeature(new FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo2", "yyy", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo3", "zzz", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final Trait trait = FlagsmithClient.getTrait(flagsAndTraits, "foo2");
        assertThat(trait)
                .isNotNull()
                .isEqualTo(trait(null, "foo2", "yyy"));
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        createFeature(new FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo2", "yyy", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo3", "zzz", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final List<Trait> traits = FlagsmithClient.getTraits(flagsAndTraits, "foo2", "foo3", "foo-missing");
        assertThat(traits)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        trait(null, "foo2", "yyy"),
                        trait(null, "foo3", "zzz")
                );
        assertThat(traits)
                .extracting(Trait::getKey)
                .doesNotContain("foo-missing");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        createFeature(new FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));
        createFeature(new ConfigFeature(
                "font_size",
                null,
                environment.projectId,
                "11pt"));

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final String fontSize = FlagsmithClient.getFeatureFlagValue("font_size", flagsAndTraits);
        assertThat(fontSize)
                .isEqualTo("11pt");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));
        final int userId = createUserIdentity("mr-user", environment.apiKey);
        switchFlagForUser(featureId, userId, true, environment.apiKey);

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final boolean enabled = FlagsmithClient.hasFeatureFlag("The Flag", flagsAndTraits);
        assertThat(enabled)
                .isTrue();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Trait_Then_Success",
                "TEST");

        assignTraitToUserIdentity("mr-user", "cookie", "value", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");
        final Trait trait = environment.client.getTrait(user, "cookie");
        assertThat(trait)
                .isEqualTo(trait(null, "cookie", "value"));
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Update_Then_Updated() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Trait_Update_Then_Updated",
                "TEST");

        assignTraitToUserIdentity("mr-user", "cookie", "old value", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo", "bar", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        assertThat(environment.client.getTrait(user, "cookie"))
                .isEqualTo(trait(null, "cookie", "old value"));

        environment.client.updateTrait(user, trait("mr-user", "cookie", "new value"));

        assertThat(environment.client.getTrait(user, "cookie"))
                .isEqualTo(trait(null, "cookie", "new value"));
        assertThat(environment.client.getTrait(user, "foo"))
                .isEqualTo(trait(null, "foo", "bar"));
    }

    @Test(groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed",
                "TEST");

        assertThatThrownBy(() ->
                environment.client.identifyUserWithTraits(null,
                        Collections.singletonList(trait(null, "x", "y"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing user Identifier");
    }

    @Test(groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_Then_Success() {
        final ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Add_Traits_For_Identity_Then_Success",
                "TEST");

        final FeatureUser user = featureUser("i-am-user-with-traits");

        final List<Trait> traits = environment.client.identifyUserWithTraits(user, Arrays.asList(
                trait(null, "trait_1", "some value1"),
                trait(null, "trait_2", "some value2")));

        assertThat(traits)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        trait(null, "trait_1", "some value1"),
                        trait(null, "trait_2", "some value2")
                );
    }

    private ProjectEnvironment createProjectEnvironment(String projectName, String environmentName) {
        final int projectId = createProject(projectName, organisationId);

        final Map<String, Object> environment = createEnvironment(environmentName, projectId);
        final String environmentApiKey = (String) environment.get("api_key");

        final FlagsmithClient client = FlagsmithClient.newBuilder()
                .withApiUrl(String.format("http://localhost:%d/api/v1/", backend.getMappedPort(BACKEND_PORT)))
                .setApiKey(environmentApiKey)
                .build();
        return new ProjectEnvironment(environmentApiKey, projectId, client);
    }

    private int createFeature(Feature feature) {
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

    private int createUserIdentity(String userIdentity, String environmentApiKey) {
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

    private Map<String, Object> createEnvironment(String name, int projectId) {
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

    private void switchFlag(int featureId, boolean enabled, String apiKey) {
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
                throw new IllegalStateException("Unable to decide which feature-state to update: " + features);
            }
        }
    }

    private void switchFlagForUser(int featureId, int userIdentityId, boolean enabled, String apiKey) {
        final List<Map<String, Object>> features = RestAssured.given()
                .headers(defaultHeaders())
                .get("/api/v1/environments/{apiKey}/identities/{identityId}/featurestates/?feature={featureId}",
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
                    .post("/api/v1/environments/{apiKey}/identities/{identityId}/featurestates/", apiKey, userIdentityId)
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
                        .put("/api/v1/environments/{apiKey}/identities/{identityId}/featurestates/{featureStateId}/",
                                apiKey, userIdentityId, featureStateId)
                        .then()
                        .statusCode(200);
            } else {
                throw new IllegalStateException("Unable to decide which feature-state to update: " + features);
            }
        }
    }

    private void assignTraitToUserIdentity(String userIdentifier, String traitKey, Object traitValue, String apiKey) {
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

    private int createProject(String name, int organisationId) {
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

    private static int createOrganisation(String name) {
        return RestAssured.given()
                .body(ImmutableMap.of("name", name))
                .headers(defaultHeaders())
                .post("/api/v1/organisations/")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getInt("id");
    }

    private static String flagSmithAuthenticate() {
        return RestAssured.given()
                .body(ImmutableMap.of(
                        // from https://github.com/Flagsmith/flagsmith-api/blob/v2.5.0/src/app/settings/common.py#L255-L258
                        "email", "admin@example.com",
                        "password", "password"
                ))
                .header("Content-type", "application/json")
                .post("/api/v1/auth/login/")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("key");
    }

    private static Headers defaultHeaders() {
        return new Headers(
                new Header("Content-type", "application/json"),
                new Header("Authorization", "Token " + token));
    }

    private Flag flag(String name, String description, boolean enabled) {
        final com.flagsmith.Feature feature = new com.flagsmith.Feature();
        feature.setName(name);
        feature.setDescription(description);
        feature.setType("FLAG");

        final Flag result = new Flag();
        result.setFeature(feature);
        result.setEnabled(enabled);
        return result;
    }

    private Flag config(String name, String description, String value) {
        final com.flagsmith.Feature feature = new com.flagsmith.Feature();
        feature.setName(name);
        feature.setDescription(description);
        feature.setType("CONFIG");

        final Flag result = new Flag();
        result.setFeature(feature);
        result.setStateValue(value);
        return result;
    }

    private Trait trait(String userIdentifier, String key, String value) {
        final Trait result = new Trait();
        if (userIdentifier != null) {
            final FeatureUser user = featureUser(userIdentifier);
            result.setIdentity(user);
        }
        result.setKey(key);
        result.setValue(value);
        return result;
    }

    private FeatureUser featureUser(String identifier) {
        final FeatureUser user = new FeatureUser();
        user.setIdentifier(identifier);
        return user;
    }

    private static abstract class Feature {
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

    private static class FlagFeature extends Feature {
        final boolean enabled;

        protected FlagFeature(String name, String description, int projectId, boolean enabled) {
            super("FLAG", name, description, projectId);
            this.enabled = enabled;
        }
    }

    private static class ConfigFeature extends Feature {
        final String value;

        protected ConfigFeature(String name, String description, int projectId, String value) {
            super("CONFIG", name, description, projectId);
            this.value = value;
        }
    }

    private static class ProjectEnvironment {
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
