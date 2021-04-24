package com.flagsmith;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

import static com.flagsmith.FlagsmithTestHelper.defaultHeaders;
import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "integration")
public class IntegrationSuiteTest {

  private static final Network network = Network.newNetwork();
  private static PostgreSQLContainer<?> postgres;

  public static final int BACKEND_PORT = 8000;

  @BeforeGroups(groups = "integration")
  public static void beforeClass() {
    postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:10.6-alpine"))
        .withNetwork(network)
        .withNetworkAliases("flagsmith-db")
        .withDatabaseName("flagsmith")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("flagsmith-db")));
    postgres.start();

    TestData.backend = new GenericContainer<>(DockerImageName.parse("flagsmith/flagsmith-api:v2.6.0"))
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
    TestData.backend.start();

    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.port = TestData.backend.getMappedPort(BACKEND_PORT);

    assertThat(RestAssured.get("/api/v1/users/init/").asString())
        .describedAs("Super user have to be created")
        .isEqualTo("ADMIN USER CREATED");

    TestData.token = flagSmithAuthenticate();
    TestData.organisationId = createOrganisation("Test Organisation");
  }

  @AfterGroups(groups = "integration")
  public static void afterClass() {
    TestData.backend.stop();
    postgres.stop();
    network.close();
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

  public static class TestData {
    public static String token;
    public static int organisationId;
    public static GenericContainer<?> backend;
  }
}
