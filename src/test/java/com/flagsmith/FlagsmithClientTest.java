package com.flagsmith;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flag;
import com.flagsmith.models.Flags;
import com.flagsmith.models.Segment;
import com.flagsmith.responses.FlagsAndTraitsResponse;
import com.flagsmith.threads.PollingManager;
import com.flagsmith.threads.RequestProcessor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import okio.Buffer;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.slf4j.Logger;

/**
 * Unit tests are env specific and will probably will need to adjust keys,
 * identities and features
 * ids etc as required.
 */
public class FlagsmithClientTest {

    private static String DEFAULT_FLAG_VALUE = "foobar";
    private static boolean DEFAULT_FLAG_STATE = true;

    private static BaseFlag defaultHandler(String featureName) {
        DefaultFlag defaultFlag = new DefaultFlag();
        defaultFlag.setEnabled(DEFAULT_FLAG_STATE);
        defaultFlag.setValue(DEFAULT_FLAG_VALUE);
        defaultFlag.setFeatureName(featureName);
        return defaultFlag;
    }

    @Test
    public void testClient_When_Cache_Disabled_Return_Null() {
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .setApiKey("api-key")
                .build();

        FlagsmithCache cache = client.getCache();

        assertNull(cache);
    }

    @Test
    public void testClient_validateObjectCreation() throws InterruptedException {
        PollingManager manager = mock(PollingManager.class);
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withPollingManager(manager)
                .withConfiguration(
                        FlagsmithConfig.newBuilder().withLocalEvaluation(Boolean.TRUE).build())
                .setApiKey("ser.abcdefg")
                .build();

        Thread.sleep(10);
        verify(manager, times(1)).startPolling();
    }

    @Test
    public void testLocalEvaluationRequiresServerKey() throws InterruptedException {
        assertThrows(RuntimeException.class, () -> FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder().withLocalEvaluation(Boolean.TRUE).build())
                .setApiKey("not-a-server-key")
                .build());
    }

    @Test
    public void testClient_errorEnvironmentApi() {
        Logger logger = mock(Logger.class);

        String baseUrl = "http://bad-url";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .enableLogging(logger)
                .setApiKey("api-key")
                .build();

        interceptor.addRule()
                .get(baseUrl + "/environment-document/")
                .respond(
                        500,
                        ResponseBody.create("error", MEDIATYPE_JSON));

        client.updateEnvironment();

        // Verify that an error was written to the log by mocking the logger and checking that a call was made
        // with the expected log message. Note that the logger will also have other invocations so we need to
        // iterate over them to check that the one we expect has been made.
        boolean found = false;
        String expectedMsg = "Unable to update environment from API. No environment configured - using defaultHandler if configured.";
        for (Invocation invocation : Mockito.mockingDetails(logger).getInvocations().stream().collect(Collectors.toList())) {
            if (invocation.getArgument(0).toString().contains(expectedMsg)) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testClient_validateEnvironment()
            throws JsonProcessingException {
        String baseUrl = "http://bad-url";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

        interceptor.addRule()
                .get(baseUrl + "/environment-document/")
                .anyTimes()
                .respond(
                        MapperFactory.getMapper().writeValueAsString(environmentModel),
                        MEDIATYPE_JSON);

        client.updateEnvironment();
        assertNotNull(client.getEnvironment());

        // Client environment should have cache initialized
        assertNotEquals(client.getEnvironment(), environmentModel);

        environmentModel.initializeCache();
        assertEquals(client.getEnvironment(), environmentModel);
    }

    @Test
    public void testClient_flagsApiException()
            throws FlagsmithApiError {
        String baseUrl = "http://bad-url";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        interceptor.addRule()
                .get(baseUrl + "/flags/")
                .respond(
                        500,
                        ResponseBody.create("error", MEDIATYPE_JSON));

        assertThrows(FlagsmithApiError.class, () -> client.getEnvironmentFlags());
    }

    @Test
    public void testClient_flagsApiEmpty()
            throws FlagsmithClientError {
        String baseUrl = "http://bad-url";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        interceptor.addRule()
                .get(baseUrl + "/flags/")
                .respond(
                        "[]",
                        MEDIATYPE_JSON);

        assertNotNull(client);
        List<BaseFlag> flags = client.getEnvironmentFlags().getAllFlags();
        assertTrue(flags.isEmpty());
    }

    @Test
    public void testClient_flagsApi()
            throws JsonProcessingException, FlagsmithClientError {
        String baseUrl = "http://bad-url";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        List<FeatureStateModel> featureStateModel = FlagsmithTestHelper.getFlags();

        interceptor.addRule()
                .get(baseUrl + "/flags/")
                .respond(
                        MapperFactory.getMapper().writeValueAsString(featureStateModel),
                        MEDIATYPE_JSON);

        List<BaseFlag> flags = client.getEnvironmentFlags().getAllFlags();
        assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
        assertEquals(flags.get(0).getValue(), "some-value");
        assertEquals(flags.get(0).getFeatureName(), "some_feature");
    }

    @Test
    public void testClient_identityFlagsApiNoTraitsException() throws FlagsmithClientError {
        String baseUrl = "http://bad-url";
        String identifier = "identifier";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        interceptor.addRule()
                .post(baseUrl + "/identities/")
                .respond(
                        500,
                        ResponseBody.create("error", MEDIATYPE_JSON));

        assertThrows(FlagsmithApiError.class, () -> client.getIdentityFlags(identifier));
    }

    @Test
    public void testClient_identityFlagsApiNoTraits() throws FlagsmithClientError {
        String baseUrl = "http://bad-url";
        String identifier = "identifier";
        MockInterceptor interceptor = new MockInterceptor();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        String json = FlagsmithTestHelper.getIdentitiesFlags();

        interceptor.addRule()
                .post(baseUrl + "/identities/")
                .respond(
                        json,
                        MEDIATYPE_JSON);

        List<BaseFlag> flags = client.getIdentityFlags(identifier).getAllFlags();
        assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
        assertEquals(flags.get(0).getValue(), "some-value");
        assertEquals(flags.get(0).getFeatureName(), "some_feature");
    }

    @Test
    public void testClient_identityFlagsApiWithTraits()
            throws FlagsmithClientError, IOException {
        String baseUrl = "http://bad-url";
        String identifier = "identifier";
        Map<String, Object> traits = new HashMap<String, Object>() {
            {
                put("some_trait", "some_value");
            }
        };
        MockInterceptor interceptor = new MockInterceptor();
        RequestProcessor requestProcessor = mock(RequestProcessor.class);
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();
        // mocking the requestor
        ((FlagsmithApiWrapper) client.getFlagsmithSdk()).setRequestor(requestProcessor);
        String json = FlagsmithTestHelper.getIdentitiesFlags();
        TypeReference<FlagsAndTraitsResponse> tr = new TypeReference<FlagsAndTraitsResponse>() {
        };

        when(requestProcessor.executeAsync(any(), any(), any()))
                .thenReturn(
                        FlagsmithTestHelper.futurableReturn(MapperFactory.getMapper().readValue(json, tr)));

        List<BaseFlag> flags = client.getIdentityFlags(identifier, traits).getAllFlags();

        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        verify(requestProcessor, times(1)).executeAsync(argument.capture(), any(), any());

        Buffer buffer = new Buffer();
        argument.getValue().body().writeTo(buffer);

        JsonNode expectedRequest = FlagsmithTestHelper.getIdentityRequest(identifier, new ArrayList<TraitModel>() {
            {
                add(new TraitModel("some_trait", "some_value"));
            }
        });

        assertEquals(expectedRequest.toString(), buffer.readUtf8());
        assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
        assertEquals(flags.get(0).getValue(), "some-value");
        assertEquals(flags.get(0).getFeatureName(), "some_feature");
    }

    @Test
    public void testClient_identityFlagsApiWithTraitsWithLocalEnvironment() {
        String baseUrl = "http://bad-url";
        String identifier = "identifier";
        Map<String, Object> traits = new HashMap<String, Object>() {
            {
                put("some_trait", "some_value");
            }
        };
        MockInterceptor interceptor = new MockInterceptor();
        RequestProcessor requestProcessor = mock(RequestProcessor.class);
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
                .setApiKey("api-key")
                .build();

        interceptor.addRule()
                .get(baseUrl + "/flags/").anyTimes()
                .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

        assertThrows(FlagsmithApiError.class,
                () -> client.getEnvironmentFlags());
    }

    @Test
    public void testClient_defaultFlagWithNoEnvironment() throws FlagsmithClientError {
        String baseUrl = "http://bad-url";
        String identifier = "identifier";
        Map<String, Object> traits = new HashMap<String, Object>() {
            {
                put("some_trait", "some_value");
            }
        };
        MockInterceptor interceptor = new MockInterceptor();
        RequestProcessor requestProcessor = mock(RequestProcessor.class);
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .build())
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
                        MEDIATYPE_JSON);

        Flags flags = client.getEnvironmentFlags();

        DefaultFlag flag = (DefaultFlag) flags.getFlag("some_feature");
        assertEquals(flag.getIsDefault(), Boolean.TRUE);
        assertEquals(flag.getEnabled(), Boolean.TRUE);
        assertEquals(flag.getValue(), "some-value");
    }

    @Test
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

        assertNotNull(cache);
    }

    @Test
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
                        MEDIATYPE_JSON);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .withLocalEvaluation(true)
                                .build())
                .setApiKey("ser.abcdefg")
                .build();

        client.updateEnvironment();

        String identifier = "identifier";
        List<Segment> segments = client.getIdentitySegments(identifier);

        assertTrue(segments.isEmpty());
    }

    @Test
    public void  testGetIdentityFlag() throws JsonProcessingException,
        FlagsmithClientError {
        String baseUrl = "http://bad-url";

        EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

        MockInterceptor interceptor = new MockInterceptor();
        interceptor.addRule()
            .get(baseUrl + "/environment-document/")
            .anyTimes()
            .respond(
                MapperFactory.getMapper().writeValueAsString(environmentModel),
                MEDIATYPE_JSON);

        FlagsmithClient client = FlagsmithClient.newBuilder()
            .withConfiguration(
                FlagsmithConfig.newBuilder()
                    .baseUri(baseUrl)
                    .addHttpInterceptor(interceptor)
                    .withLocalEvaluation(true)
                    .build())
            .setApiKey("ser.abcdefg")
            .build();

        client.updateEnvironment();

        String identifier = "identifier";
        BaseFlag flag = client.getIdentityFlag(identifier, Collections.emptyMap(), "some_feature");

        assertEquals(flag.getValue(), "some-value");
    }

    @Test
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
                        MEDIATYPE_JSON);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .withLocalEvaluation(true)
                                .build())
                .setApiKey("ser.abcdefg")
                .build();

        client.updateEnvironment();

        String identifier = "identifier";
        Map<String, Object> traits = new HashMap<String, Object>() {
            {
                put("foo", "bar");
            }
        };

        List<Segment> segments = client.getIdentitySegments(identifier, traits);

        assertEquals(segments.size(), 1);
        assertEquals(segments.get(0).getName(), "Test segment");
    }

    @Test
    public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentThrowsExceptionAndEnvironmentExists() {
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
        // we call the update environment method twice (1st should be successful, 2nd
        // will do nothing because of error)
        client.updateEnvironment();
        client.updateEnvironment();

        // Then
        // No exception is thrown and the client environment remains what was first
        // retrieved from the ApiWrapper
        assertEquals(client.getEnvironment(), environmentModel);
    }

    @Test
    public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentReturnsNullAndEnvironmentExists() {
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
        assertEquals(client.getEnvironment(), environmentModel);
    }

    @Test
    public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentReturnsNullAndEnvironmentNotExists() {
        // Given
        FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockApiWrapper.getEnvironment()).thenThrow(RuntimeException.class);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockApiWrapper)
                .withConfiguration(FlagsmithConfig.newBuilder().withLocalEvaluation(true).build())
                .setApiKey("ser.dummy-key")
                .build();

        // When
        client.updateEnvironment();

        // Then
        // The environment remains null
        assertEquals(client.getEnvironment(), null);
    }

    @Test
    public void testClose_StopsPollingManager() {
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

    @Test
    public void testClose_ClosesFlagsmithSdk() {
        // Given
        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(FlagsmithConfig.newBuilder().withLocalEvaluation(true).build())
                .setApiKey("ser.dummy-key")
                .build();

        // When
        client.close();

        // Then
        verify(mockedApiWrapper, times(1)).close();
    }

    @Test
    public void testLocalEvaluation_ReturnsConsistentResults() throws FlagsmithClientError {
        // Specific test to ensure that results are consistent when making multiple
        // calls to
        // evaluate flags soon after the client is instantiated.

        // Given
        EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();

        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEnvironment())
                .thenReturn(environmentModel)
                .thenReturn(null);
        when(mockedApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .build();

        // When
        // make 3 calls to get identity flags
        List<Flags> results = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            results.add(client.getIdentityFlags("some-identity"));
        }

        // Then
        // iterate over the results list and verify that the results are all the same
        boolean expectedState = true;
        String expectedValue = "some-value";

        for (Flags flags : results) {
            assertEquals(flags.isFeatureEnabled("some_feature"), expectedState);
            assertEquals(flags.getFeatureValue("some_feature"), expectedValue);
        }
    }

    @Test
    public void testGetEnvironmentFlags_UsesDefaultFlags_IfLocalEvaluationEnvironmentNull()
            throws FlagsmithClientError {
        // Given
        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();
        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEnvironment()).thenThrow(RuntimeException.class);
        when(mockedApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .setDefaultFlagValueFunction(FlagsmithClientTest::defaultHandler)
                .build();

        // When
        Flags environmentFlags = client.getEnvironmentFlags();

        // Then
        assertEquals(environmentFlags.getFeatureValue("foo"), DEFAULT_FLAG_VALUE);
        assertEquals(environmentFlags.isFeatureEnabled("foo"), DEFAULT_FLAG_STATE);
    }

    @Test
    public void testGetIdentityFlags_UsesDefaultFlags_IfLocalEvaluationEnvironmentNull() throws FlagsmithClientError {
        // Given
        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();
        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEnvironment()).thenThrow(RuntimeException.class);
        when(mockedApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .setDefaultFlagValueFunction(FlagsmithClientTest::defaultHandler)
                .build();

        // When
        Flags identityFlags = client.getIdentityFlags("some-identity");

        // Then
        assertEquals(identityFlags.getFeatureValue("foo"), DEFAULT_FLAG_VALUE);
        assertEquals(identityFlags.isFeatureEnabled("foo"), DEFAULT_FLAG_STATE);
    }

    @Test
    public void testClose() throws FlagsmithApiError, InterruptedException {
        // Given
        int pollingInterval = 1;

        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .withLocalEvaluation(true)
                .withEnvironmentRefreshIntervalSeconds(pollingInterval)
                .build();

        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEnvironment()).thenReturn(FlagsmithTestHelper.environmentModel());
        when(mockedApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .build();

        // When
        client.close();

        // Then
        // Since the thread will only stop once it reads the interrupt signal correctly
        // on its next polling interval, we need to wait for the polling interval
        // to complete before checking the thread has been killed correctly.
        Thread.sleep(pollingInterval);
        assertFalse(client.getPollingManager().getIsThreadAlive());
    }

    @Test
    public void testOfflineMode() throws FlagsmithClientError {
        // Given
        EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();
        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .withOfflineMode(true)
                .withOfflineHandler(new DummyOfflineHandler())
                .build();

        // When
        FlagsmithClient client = FlagsmithClient.newBuilder().withConfiguration(config).build();

        // Then
        assertEquals(environmentModel, client.getEnvironment());

        Flags environmentFlags = client.getEnvironmentFlags();
        assertTrue(environmentFlags.isFeatureEnabled("some_feature"));

        Flags identityFlags = client.getIdentityFlags("my-identity");
        assertTrue(identityFlags.isFeatureEnabled("some_feature"));
    }

    @Test
    public void testCannotUserOfflineModeWithoutOfflineHandler() throws FlagsmithRuntimeError {
        FlagsmithConfig config = FlagsmithConfig.newBuilder().withOfflineMode(true).build();

        FlagsmithRuntimeError ex = assertThrows(
                FlagsmithRuntimeError.class,
                () -> FlagsmithClient.newBuilder().withConfiguration(config).build());

        assertEquals("Offline handler must be provided to use offline mode.", ex.getMessage());
    }

    @Test
    public void testCannotUserOfflineHandlerWithLocalEvaluationMode() throws FlagsmithRuntimeError {
        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .withOfflineHandler(new DummyOfflineHandler())
                .withLocalEvaluation(true)
                .build();

        FlagsmithRuntimeError ex = assertThrows(
                FlagsmithRuntimeError.class,
                () -> FlagsmithClient.newBuilder().withConfiguration(config).build());

        assertEquals("Local evaluation and offline handler cannot be used together.", ex.getMessage());
    }

    @Test
    public void testCannotUseDefaultHandlerAndOfflineHandler() throws FlagsmithClientError {
        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .withOfflineHandler(new DummyOfflineHandler())
                .build();

        FlagsmithClient.Builder clientBuilder = FlagsmithClient
                .newBuilder()
                .withConfiguration(config)
                .setDefaultFlagValueFunction(FlagsmithClientTest::defaultHandler);

        FlagsmithRuntimeError ex = assertThrows(
                FlagsmithRuntimeError.class,
                () -> clientBuilder.build());

        assertEquals("Cannot use both default flag handler and offline handler.", ex.getMessage());
    }

    @Test
    public void testFlagsmithUsesOfflineHandlerIfSetAndNoAPIResponse() throws FlagsmithClientError {
        // Given
        MockInterceptor interceptor = new MockInterceptor();
        String baseUrl = "http://bad-url";

        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .baseUri(baseUrl)
                .addHttpInterceptor(interceptor)
                .withOfflineHandler(new DummyOfflineHandler())
                .build();
        FlagsmithClient client = FlagsmithClient
                .newBuilder()
                .withConfiguration(config)
                .setApiKey("some-key")
                .build();

        interceptor.addRule().get(baseUrl + "/flags/").respond(500);
        interceptor.addRule().post(baseUrl + "/identities/").respond(500);

        // When
        Flags environmentFlags = client.getEnvironmentFlags();
        Flags identityFlags = client.getIdentityFlags("some-identity");

        // Then
        assertTrue(environmentFlags.isFeatureEnabled("some_feature"));
        assertTrue(identityFlags.isFeatureEnabled("some_feature"));
    }
}
