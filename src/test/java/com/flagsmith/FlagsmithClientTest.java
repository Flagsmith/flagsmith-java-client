package com.flagsmith;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flags;
import com.flagsmith.models.Segment;
import com.flagsmith.threads.PollingManager;
import com.flagsmith.threads.RequestProcessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import okio.Buffer;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and features
 * ids etc as required.
 */
@Test(groups = "unit")
public class FlagsmithClientTest {

  @Test(groups = "unit")
  public void testClient_When_Cache_Disabled_Return_Null() {
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .setApiKey("api-key")
        .build();

    FlagsmithCache cache = client.getCache();

    Assert.assertNull(cache);
  }

  @Test(groups = "unit")
  public void testClient_validateObjectCreation() throws InterruptedException {
    PollingManager manager = mock(PollingManager.class);
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withPollingManager(manager)
        .withConfiguration(
            FlagsmithConfig.newBuilder().withLocalEvaluation(Boolean.TRUE).build()
        )
        .setApiKey("ser.abcdefg")
        .build();

    Thread.sleep(10);
    verify(manager, times(1)).startPolling();
  }

  @Test(groups = "unit")
  public void testLocalEvaluationRequiresServerKey() throws InterruptedException {
    Assert.assertThrows(RuntimeException.class, () -> FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder().withLocalEvaluation(Boolean.TRUE).build()
        )
        .setApiKey("not-a-server-key")
        .build());
  }

  @Test(groups = "unit")
  public void testClient_errorEnvironmentApi() {
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

    interceptor.addRule()
        .get(baseUrl + "/environment-document/")
        .respond(
            500,
            ResponseBody.create("error", MEDIATYPE_JSON)
        );

    Assert.assertThrows(Exception.class, () -> client.updateEnvironment());
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
                .build()
        ).setApiKey("api-key")
        .build();

    EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

    interceptor.addRule()
        .get(baseUrl + "/environment-document/")
        .anyTimes()
        .respond(
            MapperFactory.getMapper().writeValueAsString(environmentModel),
            MEDIATYPE_JSON
        );

    client.updateEnvironment();
    Assert.assertNotNull(client.getEnvironment());
    Assert.assertEquals(client.getEnvironment(), environmentModel);
  }

  @Test(groups = "unit")
  public void testClient_flagsApiException()
      throws FlagsmithApiError {
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

    interceptor.addRule()
        .get(baseUrl + "/flags/")
        .respond(
            500,
            ResponseBody.create("error", MEDIATYPE_JSON)
        );

    Assert.assertThrows(FlagsmithApiError.class, () -> client.getEnvironmentFlags());
  }

  @Test(groups = "unit")
  public void testClient_flagsApiEmpty()
      throws FlagsmithApiError {
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

    interceptor.addRule()
        .get(baseUrl + "/flags/")
        .respond(
            "[]",
            MEDIATYPE_JSON
        );

    Assert.assertNotNull(client);
    List<BaseFlag> flags = client.getEnvironmentFlags().getAllFlags();
    Assert.assertTrue(flags.isEmpty());
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
            MapperFactory.getMapper().writeValueAsString(featureStateModel),
            MEDIATYPE_JSON
        );

    List<BaseFlag> flags = client.getEnvironmentFlags().getAllFlags();
    Assert.assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
    Assert.assertEquals(flags.get(0).getValue(), "some-value");
    Assert.assertEquals(flags.get(0).getFeatureName(), "some_feature");
  }

  @Test(groups = "unit")
  public void testClient_identityFlagsApiNoTraitsException() throws FlagsmithClientError {
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

    interceptor.addRule()
        .post(baseUrl + "/identities/")
        .respond(
            500,
            ResponseBody.create("error", MEDIATYPE_JSON)
        );

    Assert.assertThrows(FlagsmithApiError.class, () -> client.getIdentityFlags(identifier));
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
    Map<String, Object> traits = new HashMap<String, Object>() {{
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
                MapperFactory.getMapper().readTree(json)
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
    Map<String, Object> traits = new HashMap<String, Object>() {{
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
    Map<String, Object> traits = new HashMap<String, Object>() {{
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

  @Test(groups = "unit")
  public void testClient_When_Cache_Enabled_Return_Cache_Obj() {
    FlagsmithClient client = FlagsmithClient.newBuilder()
        .setApiKey("api-key")
        .withCache(FlagsmithCacheConfig
            .newBuilder()
            .enableEnvLevelCaching("newkey-random-name")
            .maxSize(2)
            .build())
        .build();

    FlagsmithCache cache = client.getCache();

    Assert.assertNotNull(cache);
  }

  @Test(groups = "unit")
  public void testGetIdentitySegmentsNoTraits() throws JsonProcessingException,
      FlagsmithClientError {
    String baseUrl = "http://bad-url";

    EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

    MockInterceptor interceptor = new MockInterceptor();
    interceptor.addRule()
        .get(baseUrl + "/environment-document/")
        .anyTimes()
        .respond(
            MapperFactory.getMapper().writeValueAsString(environmentModel),
            MEDIATYPE_JSON
        );

    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .withLocalEvaluation(true)
                .build()
        ).setApiKey("ser.abcdefg")
        .build();

    client.updateEnvironment();

    String identifier = "identifier";
    List<Segment> segments = client.getIdentitySegments(identifier);

    Assert.assertTrue(segments.isEmpty());
  }


  @Test(groups = "unit")
  public void testGetIdentitySegmentsWithValidTrait() throws JsonProcessingException,
      FlagsmithClientError {
    String baseUrl = "http://bad-url";

    EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

    MockInterceptor interceptor = new MockInterceptor();
    interceptor.addRule()
        .get(baseUrl + "/environment-document/")
        .anyTimes()
        .respond(
            MapperFactory.getMapper().writeValueAsString(environmentModel),
            MEDIATYPE_JSON
        );

    FlagsmithClient client = FlagsmithClient.newBuilder()
        .withConfiguration(
            FlagsmithConfig.newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .withLocalEvaluation(true)
                .build()
        ).setApiKey("ser.abcdefg")
        .build();

    client.updateEnvironment();

    String identifier = "identifier";
    Map<String, Object> traits = new HashMap<String, Object>(){{
      put("foo", "bar");
    }};

    List<Segment> segments = client.getIdentitySegments(identifier, traits);

    Assert.assertEquals(segments.size(), 1);
    Assert.assertEquals(segments.get(0).getName(), "Test segment");
  }

  @Test(groups = "unit")
  public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentThrowsException() {
    // Given
    EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

    FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
    when(mockApiWrapper.getEnvironment())
            .thenReturn(environmentModel)
            .thenThrow(RuntimeException.class);

    FlagsmithClient client = FlagsmithClient.newBuilder()
            .withFlagsmithApiWrapper(mockApiWrapper)
            .withConfiguration(FlagsmithConfig.newBuilder().withLocalEvaluation(true).build())
            .setApiKey("ser.dummy-key")
            .build();

    // When
    // we call the update environment method twice (1st should be successful, 2nd will do nothing because of error)
    client.updateEnvironment();
    client.updateEnvironment();

    // Then
    // No exception is thrown and the client environment remains what was first retrieved from the ApiWrapper
    Assert.assertEquals(client.getEnvironment(), environmentModel);
  }

  @Test(groups = "unit")
  public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentReturnsNull() {
    // Given
    EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

    FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
    when(mockApiWrapper.getEnvironment())
            .thenReturn(environmentModel)
            .thenReturn(null);

    FlagsmithClient client = FlagsmithClient.newBuilder()
            .withFlagsmithApiWrapper(mockApiWrapper)
            .withConfiguration(FlagsmithConfig.newBuilder().withLocalEvaluation(true).build())
            .setApiKey("ser.dummy-key")
            .build();

    // When
    // we call the update environment method twice
    // (1st should be successful, 2nd will do nothing because of null return)
    client.updateEnvironment();
    client.updateEnvironment();

    // Then
    // The client environment is not overwritten with null
    Assert.assertEquals(client.getEnvironment(), environmentModel);
  }

  @Test(groups = "unit")
  public void testClose_StopsPolling() {
    // Given
    PollingManager mockedPollingManager = mock(PollingManager.class);
    FlagsmithClient client = FlagsmithClient.newBuilder()
            .withPollingManager(mockedPollingManager)
            .withConfiguration(FlagsmithConfig.newBuilder().withLocalEvaluation(true).build())
            .setApiKey("ser.dummy-key")
            .build();

    // When
    client.close();

    // Then
    verify(mockedPollingManager, times(1)).stopPolling();
  }
}
