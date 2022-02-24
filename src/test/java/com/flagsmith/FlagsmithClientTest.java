package com.flagsmith;

import static com.flagsmith.FlagsmithTestHelper.config;
import static com.flagsmith.FlagsmithTestHelper.createFeature;
import static com.flagsmith.FlagsmithTestHelper.createProjectEnvironment;
import static com.flagsmith.FlagsmithTestHelper.createUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.flag;
import static com.flagsmith.FlagsmithTestHelper.switchFlagForUser;
import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.utils.encode.JsonEncoder;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flag;
import com.flagsmith.models.Flags;
import com.flagsmith.threads.PollingManager;
import com.flagsmith.threads.RequestProcessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import okio.Buffer;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and features
 * ids etc as required.
 */
@Test(groups = "unit")
public class FlagsmithClientTest {

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_Features_Then_Success() throws FlagsmithApiError {
    final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
        "testClient_When_Get_Features_Then_Success",
        "TEST");

    createFeature(new FlagsmithTestHelper.FlagFeature(
        "Flag 1",
        "Description for Flag 1",
        environment.projectId,
        true));
    createFeature(new FlagsmithTestHelper.FlagFeature(
        "Flag 2",
        "Description for Flag 2",
        environment.projectId,
        false));
    createFeature(new FlagsmithTestHelper.ConfigFeature(
        "Config 1",
        "Description for Config 1",
        environment.projectId,
        "xxx"));
    createFeature(new FlagsmithTestHelper.ConfigFeature(
        "Config 2",
        "Description for Config 2",
        environment.projectId,
        "foo"));

    final Flags featureFlags = environment.client.getEnvironmentFlags();

    assertThat(featureFlags.getFlags().values())
        .isNotNull()
        .hasSize(4)
        .containsExactlyInAnyOrder(
            flag("Flag 1", "Description for Flag 1", true),
            flag("Flag 2", "Description for Flag 2", false),
            config("Config 1", "Description for Config 1", "xxx"),
            config("Config 2", "Description for Config 2", "foo")
        );
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_Features_For_User_Then_Success() throws FlagsmithApiError {
    final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
        "testClient_When_Get_Features_For_User_Then_Success",
        "TEST");

    final int featureId = createFeature(new FlagsmithTestHelper.FlagFeature(
        "Flag to be enabled for the user",
        null,
        environment.projectId,
        false));
    createFeature(new FlagsmithTestHelper.FlagFeature(
        "Other Flag",
        null,
        environment.projectId,
        false));

    createUserIdentity("first-user", environment.apiKey);
    final int secondUserId = createUserIdentity("second-user", environment.apiKey);

    switchFlagForUser(featureId, secondUserId, true, environment.apiKey);

    final String user = "second-user";

    final Flags listForUserEnabled = environment.client.getEnvironmentFlags();
    assertThat(listForUserEnabled.getFlags().values())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, true),
            flag("Other Flag", null, false)
        );

    final Flags listWithoutUser = environment.client.getEnvironmentFlags();
    assertThat(listWithoutUser.getFlags().values())
        .hasSize(2)
        .allSatisfy(flag -> assertThat(flag.getEnabled()).isFalse());

    switchFlagForUser(featureId, secondUserId, false, environment.apiKey);

    final Flags listForUserDisabled = environment.client.getEnvironmentFlags();
    assertThat(listForUserDisabled.getFlags().values())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, false),
            flag("Other Flag", null, false)
        );
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Cache_Disabled_Return_Null() {
    final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
        "testClient_When_Cache_Disabled_Return_Null",
        "TEST");

    FlagsmithCache cache = environment.client.getCache();

    assertNull(cache);
  }

  @BeforeMethod(groups = "unit")
  public void init() {

  }

  @Test(groups = "unit")
  public void testClient_validateObjectCreation() throws InterruptedException {
    PollingManager manager = mock(PollingManager.class);
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withPollingManager(manager)
        .withConfiguration(
            FlagsmithConfig.newBuilder().withLocalEvaluation(Boolean.TRUE).build()
        )
        .build();
    Thread.sleep(10);
    verify(manager, times(1)).startPolling();
  }

  @Test(groups = "unit")
  public void testClient_validateEnvironment()
      throws JsonProcessingException {
    String baseUrl = "http://bad-url";
    MockInterceptor interceptor = new MockInterceptor();
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .withLocalEvaluation(Boolean.TRUE)
                .build()
        ).setApiKey("api-key")
        .build();

    EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

    interceptor.addRule()
        .get(baseUrl + "/environment-document/")
        .respond(
            MapperFactory.getMappper().writeValueAsString(environmentModel),
            MEDIATYPE_JSON
        );

    client.updateEnvironment();
    Assert.assertNotNull(client.getEnvironment());
    Assert.assertEquals(client.getEnvironment(), environmentModel);
  }

  @Test(groups = "unit")
  public void testClient_flagsApi()
      throws JsonProcessingException, FlagsmithApiError {
    String baseUrl = "http://bad-url";
    MockInterceptor interceptor = new MockInterceptor();
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .build()
        ).setApiKey("api-key")
        .build();

    List<FeatureStateModel> featureStateModel = FlagsmithTestHelper.getFlags();

    interceptor.addRule()
        .get(baseUrl + "/flags/")
        .respond(
            MapperFactory.getMappper().writeValueAsString(featureStateModel),
            MEDIATYPE_JSON
        );

    List<BaseFlag> flags = client.getEnvironmentFlags().getAllFlags();
    Assert.assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
    Assert.assertEquals(flags.get(0).getValue(), "some-value");
    Assert.assertEquals(flags.get(0).getFeatureName(), "some_feature");
  }

  @Test(groups = "unit")
  public void testClient_identityFlagsApiNoTraits() throws FlagsmithClientError {
    String baseUrl = "http://bad-url";
    String identifier = "identifier";
    MockInterceptor interceptor = new MockInterceptor();
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .build()
        ).setApiKey("api-key")
        .build();

    String json = FlagsmithTestHelper.getIdentitiesFlags();

    interceptor.addRule()
        .post(baseUrl + "/identities/")
        .respond(
            json,
            MEDIATYPE_JSON
        );

    List<BaseFlag> flags = client.getIdentityFlags(identifier).getAllFlags();
    Assert.assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
    Assert.assertEquals(((JsonNode) flags.get(0).getValue()).asText(), "some-value");
    Assert.assertEquals(flags.get(0).getFeatureName(), "some_feature");
  }

  @Test(groups = "unit")
  public void testClient_identityFlagsApiWithTraits()
      throws FlagsmithClientError, IOException {
    String baseUrl = "http://bad-url";
    String identifier = "identifier";
    Map<String, String> traits = new HashMap<String, String>() {{
      put("some_trait", "some_value");
    }};
    MockInterceptor interceptor = new MockInterceptor();
    RequestProcessor requestProcessor = mock(RequestProcessor.class);
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .build()
        ).setApiKey("api-key")
        .build();
    // mocking the requestor
    ((FlagsmithApiWrapper) client.getFlagsmithSdk()).setRequestor(requestProcessor);
    String json = FlagsmithTestHelper.getIdentitiesFlags();

    when(requestProcessor.executeAsync(any(), any()))
        .thenReturn(
            FlagsmithTestHelper.futurableReturn(
                JsonEncoder.getMapper().readTree(json)
            ));

    List<BaseFlag> flags = client.getIdentityFlags(identifier, traits).getAllFlags();

    ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
    verify(requestProcessor, times(1)).executeAsync(argument.capture(), any());

    Buffer buffer = new Buffer();
    argument.getValue().body().writeTo(buffer);

    JsonNode expectedRequest = FlagsmithTestHelper.getIdentityRequest(identifier, new ArrayList<TraitModel>() {{
      add(new TraitModel("some_trait", "some_value"));
    }});

    Assert.assertEquals(expectedRequest.toString(), buffer.readUtf8());
    Assert.assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
    Assert.assertEquals(((JsonNode) flags.get(0).getValue()).asText(), "some-value");
    Assert.assertEquals(flags.get(0).getFeatureName(), "some_feature");
  }

  @Test(groups = "unit")
  public void testClient_identityFlagsApiWithTraitsWithLocalEnvironment() {
    String baseUrl = "http://bad-url";
    String identifier = "identifier";
    Map<String, String> traits = new HashMap<String, String>() {{
      put("some_trait", "some_value");
    }};
    MockInterceptor interceptor = new MockInterceptor();
    RequestProcessor requestProcessor = mock(RequestProcessor.class);
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .build()
        ).setApiKey("api-key")
        .build();

    interceptor.addRule()
        .get(baseUrl + "/flags/").anyTimes()
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    assertThrows(FlagsmithApiError.class,
        () -> client.getEnvironmentFlags());
  }

  @Test(groups = "unit")
  public void testClient_defaultFlagWithNoEnvironment() throws FlagsmithClientError {
    String baseUrl = "http://bad-url";
    String identifier = "identifier";
    Map<String, String> traits = new HashMap<String, String>() {{
      put("some_trait", "some_value");
    }};
    MockInterceptor interceptor = new MockInterceptor();
    RequestProcessor requestProcessor = mock(RequestProcessor.class);
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .build()
        )
        .setApiKey("api-key")
        .setDefaultFlagValueFunction((name) -> {
          DefaultFlag flag = new DefaultFlag();
          flag.setValue("some-value");
          flag.setEnabled(true);

          return flag;
        })
        .build();

    interceptor.addRule()
        .get(baseUrl + "/flags/")
        .respond(
            "[]",
            MEDIATYPE_JSON
        );

    Flags flags = client.getEnvironmentFlags();

    DefaultFlag flag = (DefaultFlag) flags.getFlag("some_feature");
    Assert.assertEquals(flag.getIsDefault(), Boolean.TRUE);
    Assert.assertEquals(flag.getEnabled(), Boolean.TRUE);
    Assert.assertEquals(flag.getValue(), "some-value");
  }
}
