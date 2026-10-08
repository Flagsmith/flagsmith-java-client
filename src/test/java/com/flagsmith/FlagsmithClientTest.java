package com.flagsmith;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FeatureNotFoundError;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.EvaluationResult;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.environments.EnvironmentModel;
import com.flagsmith.models.features.FeatureStateModel;
import com.flagsmith.models.Flag;
import com.flagsmith.models.Flags;
import com.flagsmith.models.SdkTraitModel;
import com.flagsmith.models.Segment;
import com.flagsmith.models.TraitConfig;
import com.flagsmith.models.TraitModel;
import com.flagsmith.responses.FlagsAndTraitsResponse;
import com.flagsmith.threads.EventProcessor;
import com.flagsmith.threads.PollingManager;
import com.flagsmith.threads.RequestProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import okio.Buffer;
import com.flagsmith.flagengine.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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

        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();

        interceptor.addRule()
                .get(baseUrl + "/environment-document/")
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
                .anyTimes()
                .respond(
                        FlagsmithTestHelper.environmentString(),
                        MEDIATYPE_JSON);

        client.updateEnvironment();
        assertNotNull(client.getEvaluationContext());
        assertEquals(client.getEvaluationContext(), evaluationContext);
    }

    @Test
    public void testClient_updateEnvironmentPaginatesIdentityOverrides()
            throws FlagsmithClientError {
        String baseUrl = "http://bad-url";
        String page2Id = "identity_override:1:00000000-0000-0000-0000-000000000001";
        String page3Id = "identity_override:1:00000000-0000-0000-0000-000000000002";
        MockInterceptor interceptor = new MockInterceptor();

        interceptor.addRule()
                .get(baseUrl + "/environment-document/")
                .headerMatches("X-Environment-Key", Pattern.compile("ser.abcdefg"))
                .respond(
                        FlagsmithTestHelper.environmentString(),
                        MEDIATYPE_JSON)
                .header("Link", "</environment-document/?page_id=identity_override%3A1%3A"
                        + "00000000-0000-0000-0000-000000000001>; rel=\"next\"");

        interceptor.addRule()
                .get()
                .urlStarts(baseUrl + "/environment-document/")
                .paramMatches("page_id", Pattern.compile(Pattern.quote(page2Id)))
                .respond(environmentPageString("overridden-identity-page-2"), MEDIATYPE_JSON)
                .header("Link", "</environment-document/?page_id=identity_override%3A1%3A"
                        + "00000000-0000-0000-0000-000000000002>; rel=\"next\"");

        interceptor.addRule()
                .get()
                .urlStarts(baseUrl + "/environment-document/")
                .paramMatches("page_id", Pattern.compile(Pattern.quote(page3Id)))
                .respond(environmentPageString("overridden-identity-page-3"), MEDIATYPE_JSON);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withPollingManager(mock(PollingManager.class))
                .withConfiguration(
                        FlagsmithConfig.newBuilder()
                                .baseUri(baseUrl)
                                .addHttpInterceptor(interceptor)
                                .withLocalEvaluation(true)
                                .build())
                .setApiKey("ser.abcdefg")
                .build();

        client.updateEnvironment();

        assertEquals("overridden-value",
                client.getIdentityFlags("overridden-identity").getFeatureValue("some_feature"));
        assertEquals("overridden-value",
                client.getIdentityFlags("overridden-identity-page-2")
                        .getFeatureValue("some_feature"));
        assertEquals("overridden-value",
                client.getIdentityFlags("overridden-identity-page-3")
                        .getFeatureValue("some_feature"));

        assertEquals("some-value", client.getEnvironmentFlags().getFeatureValue("some_feature"));
    }

    private static String environmentPageString(String identifier) {
        return "{\n"
                + "  \"api_key\": \"B62qaMZNwfiqT76p38ggrQ\",\n"
                + "  \"feature_states\": [\n"
                + "    {\n"
                + "      \"feature_state_value\": \"page-value\",\n"
                + "      \"feature\": {\"name\": \"some_feature\", \"type\": \"STANDARD\", \"id\": 1},\n"
                + "      \"enabled\": true\n"
                + "    },\n"
                + "    {\n"
                + "      \"feature_state_value\": \"page-value\",\n"
                + "      \"feature\": {\"name\": \"page_only_feature\", \"type\": \"STANDARD\", \"id\": 2},\n"
                + "      \"enabled\": true\n"
                + "    }\n"
                + "  ],\n"
                + "  \"identity_overrides\": [\n"
                + "    {\n"
                + "      \"identifier\": \"" + identifier + "\",\n"
                + "      \"identity_features\": [\n"
                + "        {\n"
                + "          \"feature_state_value\": \"overridden-value\",\n"
                + "          \"feature\": {\"name\": \"some_feature\", \"type\": \"STANDARD\", \"id\": 1},\n"
                + "          \"enabled\": true\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
                .respond(
                        json,
                        MEDIATYPE_JSON);

        List<BaseFlag> flags = client.getIdentityFlags(identifier).getAllFlags();
        assertEquals(flags.get(0).getEnabled(), Boolean.TRUE);
        assertEquals(flags.get(0).getValue(), "some-value");
        assertEquals(flags.get(0).getFeatureName(), "some_feature");
    }

    private static Stream<Arguments> dataProviderForIdentityFlagsApiWithTraitsTest() {
        return Stream.of(
            Arguments.of(
                "identifier",
                false,
                new HashMap<String, Object>() {
                {
                    put("some_trait", "some_value");
                    put("transient_trait", new TraitConfig("transient_value", true));
                }
            }, FlagsmithTestHelper.getIdentityRequest("identifier", new ArrayList<SdkTraitModel>() {
                {
                    add(
                            SdkTraitModel.builder()
                                    .traitKey("some_trait")
                                    .traitValue("some_value")
                                    .build()
                    );
                    add(
                            SdkTraitModel.builder()
                                    .traitKey("transient_trait")
                                    .traitValue("transient_value")
                                    .isTransient(true)
                                    .build()
                    );
                }
            })),
            Arguments.of(
                "transient-identifier",
                true,
                new HashMap<String, Object>() {
                {
                    put("some_trait", "some_value");
                }
            }, FlagsmithTestHelper.getIdentityRequest("transient-identifier", new ArrayList<TraitModel>() {
                {
                    add(
                            TraitModel.builder()
                                    .traitKey("some_trait")
                                    .traitValue("some_value")
                                    .build()
                    );
                }
            }, true))
        );
    }

    @ParameterizedTest
    @MethodSource("dataProviderForIdentityFlagsApiWithTraitsTest")
    public void testClient_identityFlagsApiWithTraits(
        String identifier, boolean isTransient, Map<String, Object> traits, JsonNode expectedRequest)
            throws FlagsmithClientError, IOException {
        String baseUrl = "http://bad-url";
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

        List<BaseFlag> flags = client.getIdentityFlags(identifier, traits, isTransient).getAllFlags();

        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        verify(requestProcessor, times(1)).executeAsync(argument.capture(), any(), any());

        Buffer buffer = new Buffer();
        argument.getValue().body().writeTo(buffer);

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
                .get(baseUrl + "/flags/")
                .anyTimes()
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
                .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
                .headerMatches("X-Environment-Key", Pattern.compile("ser.abcdefg"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
    public void testGetIdentitySegmentsWithValidTrait() throws JsonProcessingException,
            FlagsmithClientError {
        String baseUrl = "http://bad-url";

        EnvironmentModel environmentModel = FlagsmithTestHelper.environmentModel();

        MockInterceptor interceptor = new MockInterceptor();
        interceptor.addRule()
                .get(baseUrl + "/environment-document/")
                .headerMatches("X-Environment-Key", Pattern.compile("ser.abcdefg"))
                .headerMatches("User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
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
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();

        FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockApiWrapper.getEvaluationContext())
                .thenReturn(evaluationContext)
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
        assertEquals(client.getEvaluationContext(), evaluationContext);
    }

    @Test
    public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentReturnsNullAndEnvironmentExists() {
        // Given
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();

        FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockApiWrapper.getEvaluationContext())
                .thenReturn(evaluationContext)
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
        assertEquals(client.getEvaluationContext(), evaluationContext);
    }

    @Test
    public void testUpdateEnvironment_DoesNothing_WhenGetEnvironmentReturnsNullAndEnvironmentNotExists() {
        // Given
        FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockApiWrapper.getEvaluationContext()).thenThrow(RuntimeException.class);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockApiWrapper)
                .withConfiguration(FlagsmithConfig.newBuilder().withLocalEvaluation(true).build())
                .setApiKey("ser.dummy-key")
                .build();

        // When
        client.updateEnvironment();

        // Then
        // The environment remains null
        assertEquals(client.getEvaluationContext(), null);
    }

    @Test
    public void testUpdateEnvironment_StoresIdentityOverrides_WhenGetEnvironmentReturnsEnvironmentWithOverrides()
        throws FlagsmithClientError {
        // Given
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();

        FlagsmithConfig config = FlagsmithConfig.newBuilder()
                        .withLocalEvaluation(true)
                        .build();

        FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockApiWrapper.getEvaluationContext()).thenReturn(evaluationContext);
        when(mockApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .build();

        // When
        client.updateEnvironment();

        // Then
        // Identity overrides are correctly stored
        assertEquals(
                client.getIdentityFlags("overridden-identity")
                        .getFlag("some_feature").getValue(),
                "overridden-value");
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
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();

        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();

        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEvaluationContext())
                .thenReturn(evaluationContext)
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
    public void testLocalEvaluation_ReturnsIdentityOverrides() throws FlagsmithClientError {
        // Given
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();

        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();

        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEvaluationContext())
                .thenReturn(evaluationContext)
                .thenReturn(null);
        when(mockedApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .build();

        Flags flagsWithoutOverride = client.getIdentityFlags("test");

        // When
        Flags flagsWithOverride = client.getIdentityFlags("overridden-identity");

        // Then
        assertEquals(flagsWithoutOverride.getFeatureValue("some_feature"), "some-value");
        assertEquals(flagsWithOverride.getFeatureValue("some_feature"), "overridden-value");
    }

    @Test
    public void testLocalEvaluation_getEnvironmentFlags_NoTargeting() throws FlagsmithClientError {
        // Given
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();
        EvaluationResult evaluationResult = Engine.getEvaluationResult(
                new EvaluationContext(evaluationContext)
                        .withSegments(null)
        );

        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();

        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEvaluationContext())
                .thenReturn(evaluationContext);
        when(mockedApiWrapper.getConfig()).thenReturn(config);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(mockedApiWrapper)
                .withConfiguration(config)
                .setApiKey("ser.dummy-key")
                .build();

        // When
        try (MockedStatic<Engine> mockedEngine = mockStatic(Engine.class)) {
                mockedEngine.when(
                        () -> Engine.getEvaluationResult(
                                new EvaluationContext(evaluationContext)
                                        .withSegments(null)
                        )
                ).thenReturn(evaluationResult);

                client.getEnvironmentFlags();

                // Then
                mockedEngine.verify(
                        () -> Engine.getEvaluationResult(
                                new EvaluationContext(evaluationContext)
                                        .withSegments(null)
                        )
                );
        }
    }

    @Test
    public void testGetEnvironmentFlags_UsesDefaultFlags_IfLocalEvaluationEnvironmentNull()
            throws FlagsmithClientError {
        // Given
        FlagsmithConfig config = FlagsmithConfig.newBuilder().withLocalEvaluation(true).build();
        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEvaluationContext()).thenThrow(RuntimeException.class);
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
        when(mockedApiWrapper.getEvaluationContext()).thenThrow(RuntimeException.class);
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
        int pollingIntervalSeconds = 1;

        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .withLocalEvaluation(true)
                .withEnvironmentRefreshIntervalSeconds(pollingIntervalSeconds)
                .build();

        FlagsmithApiWrapper mockedApiWrapper = mock(FlagsmithApiWrapper.class);
        when(mockedApiWrapper.getEvaluationContext()).thenReturn(FlagsmithTestHelper.evaluationContext());
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
        Thread.sleep((pollingIntervalSeconds * 1000) + 100);
        assertFalse(client.getPollingManager().getIsThreadAlive());
    }

    @Test
    public void testOfflineMode() throws FlagsmithClientError {
        // Given
        EvaluationContext evaluationContext = FlagsmithTestHelper.evaluationContext();
        FlagsmithConfig config = FlagsmithConfig
                .newBuilder()
                .withOfflineMode(true)
                .withOfflineHandler(new DummyOfflineHandler())
                .build();

        // When
        FlagsmithClient client = FlagsmithClient.newBuilder().withConfiguration(config).build();

        // Then
        assertEquals(evaluationContext, client.getEvaluationContext());

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

    /** A client on a mock event processor, serving the experiment identity flags or none. */
    private static FlagsmithClient experimentClient(
            EventProcessor processor, boolean withDefaultHandler, boolean flagsUnavailable) {
        MockInterceptor interceptor = new MockInterceptor();
        interceptor.addRule()
                .post("http://bad-url/identities/")
                .anyTimes()
                .respond(FlagsmithTestHelper.getIdentitiesFlagsWithExperiment(), MEDIATYPE_JSON);
        FlagsmithConfig config = FlagsmithConfig.newBuilder()
                .baseUri("http://bad-url")
                .addHttpInterceptor(interceptor)
                .withEventProcessor(processor)
                .build();
        FlagsmithClient.Builder builder = FlagsmithClient.newBuilder()
                .withConfiguration(config)
                .setApiKey("api-key");
        if (withDefaultHandler) {
            builder.setDefaultFlagValueFunction(FlagsmithClientTest::defaultHandler);
        }
        if (flagsUnavailable) {
            FlagsmithApiWrapper mockApiWrapper = mock(FlagsmithApiWrapper.class);
            when(mockApiWrapper.getConfig()).thenReturn(config);
            when(mockApiWrapper.identifyUserWithTraits(any(), any(), anyBoolean(), anyBoolean()))
                    .thenReturn(null);
            builder.withFlagsmithApiWrapper(mockApiWrapper);
        }
        return builder.build();
    }

    private static FlagsmithClient experimentClient(EventProcessor processor) {
        return experimentClient(processor, false, false);
    }

    private static Stream<Arguments> invalidEventsConfigs() {
        return Stream.of(
                Arguments.of(FlagsmithConfig.newBuilder().withEventsMaxBufferItems(10)),
                Arguments.of(FlagsmithConfig.newBuilder().withEventsFlushIntervalMillis(10)),
                Arguments.of(FlagsmithConfig.newBuilder()
                        .withEnableEvents(Boolean.TRUE).withEventsMaxBufferItems(0)),
                Arguments.of(FlagsmithConfig.newBuilder()
                        .withEnableEvents(Boolean.TRUE).withEventsFlushIntervalMillis(-1)));
    }

    @ParameterizedTest
    @MethodSource("invalidEventsConfigs")
    public void testInvalidEventsConfigThrowsAtBuild(FlagsmithConfig.Builder builder) {
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void testEventsStayDisabledUnlessEnabled() {
        FlagsmithConfig config = FlagsmithConfig.newBuilder().eventsUri("http://events-uri").build();

        assertFalse(config.getEnableEvents());
        assertEquals("http://events-uri/", config.getEventsUri().toString());
        assertFalse(FlagsmithConfig.newBuilder().withEnableEvents(null).build().getEnableEvents());
    }

    @Test
    public void testEventsSettingsReachTheProcessor() {
        FlagsmithConfig config = FlagsmithConfig.newBuilder()
                .eventsUri("http://events-uri")
                .withEnableEvents(Boolean.TRUE)
                .withEventsMaxBufferItems(5)
                .withEventsFlushIntervalMillis(0)
                .build();
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(config)
                .setApiKey("api-key")
                .build();

        EventProcessor processor = client.getEventProcessor();
        assertEquals("http://events-uri/v1/events", processor.getEventsEndpoint().toString());
        assertEquals(5, processor.getMaxBufferItems());
        assertEquals(0, processor.getFlushIntervalMillis());
        client.close();
    }

    @Test
    public void testEventsInOfflineModeThrowsAtBuild() {
        FlagsmithClient.Builder clientBuilder = FlagsmithClient.newBuilder()
                .withConfiguration(FlagsmithConfig.newBuilder()
                        .withOfflineMode(Boolean.TRUE)
                        .withOfflineHandler(new DummyOfflineHandler())
                        .withEventProcessor(mock(EventProcessor.class))
                        .build())
                .setApiKey("api-key");

        FlagsmithRuntimeError ex = assertThrows(FlagsmithRuntimeError.class, clientBuilder::build);
        assertEquals("Events cannot be enabled in offline mode.", ex.getMessage());
    }

    @Test
    public void testFailedBuildDoesNotStartTheEventProcessor() {
        EventProcessor processor = mock(EventProcessor.class);
        FlagsmithClient.Builder clientBuilder = FlagsmithClient.newBuilder()
                .withConfiguration(FlagsmithConfig.newBuilder()
                        .withLocalEvaluation(true)
                        .withEventProcessor(processor)
                        .build())
                // Local evaluation needs a server key, so this build fails.
                .setApiKey("api-key");

        assertThrows(FlagsmithRuntimeError.class, clientBuilder::build);
        verify(processor, never()).claim();
        verify(processor, never()).start();
    }

    @Test
    public void testEventApisThrowWhenEventsAreDisabled() {
        FlagsmithClient client = FlagsmithClient.newBuilder().setApiKey("api-key").build();

        assertThrows(FlagsmithRuntimeError.class,
                () -> client.getExperimentFlag("checkout_cta", "user-1"));
        assertThrows(FlagsmithRuntimeError.class, () -> client.trackEvent("purchase"));
        assertThrows(FlagsmithRuntimeError.class,
                () -> client.trackExposureEvent("checkout_cta", "user-1", "treatment"));
        assertTrue(client.flushEvents().isDone());
    }

    private static Stream<Consumer<FlagsmithClient>> invalidEventCalls() {
        return Stream.of(
                (client) -> client.trackEvent("$flag_exposure"),
                (client) -> client.trackEvent(null),
                (client) -> client.trackEvent("  ", "user-1"),
                (client) -> client.trackExposureEvent(null, "user-1", "treatment"),
                (client) -> client.trackExposureEvent("", "user-1", "treatment"));
    }

    @ParameterizedTest
    @MethodSource("invalidEventCalls")
    public void testEventApisRejectReservedAndBlankNames(Consumer<FlagsmithClient> call) {
        EventProcessor processor = mock(EventProcessor.class);
        FlagsmithClient client = experimentClient(processor);

        assertThrows(IllegalArgumentException.class, () -> call.accept(client));
        verify(processor, never()).trackEvent(any(), any(), any(), any(), any());
        verify(processor, never()).trackExposureEvent(any(), any(), any(), any(), any());
    }

    @Test
    public void testTrackExposureEventWithBlankIdentifierSendsNothing() {
        EventProcessor processor = mock(EventProcessor.class);
        FlagsmithClient client = experimentClient(processor);

        client.trackExposureEvent("checkout_cta", "  ", "treatment");
        client.trackExposureEvent("checkout_cta", null, "treatment");

        verify(processor, never()).trackExposureEvent(any(), any(), any(), any(), any());
    }

    @Test
    public void testGetExperimentFlagRecordsAnExposurePerIdentityWhenEnrolled()
            throws FlagsmithClientError {
        EventProcessor processor = mock(EventProcessor.class);
        FlagsmithClient client = experimentClient(processor);
        Map<String, Object> traits = new HashMap<>();
        traits.put("plan", "premium");
        traits.put("session_id", new TraitConfig("abc123", true));

        Flag flag = (Flag) client.getExperimentFlag("checkout_cta", "user-1", traits);
        client.getExperimentFlag("checkout_cta", "user-2");

        assertEquals("treatment", flag.getVariant());
        assertEquals("SPLIT; weight=70.0", flag.getReason());
        assertEquals(42, flag.getExperiment().getId());
        assertEquals(Boolean.TRUE, flag.getExperiment().getInExperiment());
        Map<String, Object> metadata = Collections.singletonMap("experiment_id", 42);
        verify(processor).trackExposureEvent(
                "checkout_cta", "user-1", "treatment", traits, metadata);
        verify(processor).trackExposureEvent(
                "checkout_cta", "user-2", "treatment", new HashMap<>(), metadata);
    }

    @Test
    public void testGetExperimentFlagRecordsNoExposureWhenNotEnrolledOrDisabled()
            throws FlagsmithClientError {
        EventProcessor processor = mock(EventProcessor.class);
        FlagsmithClient client = experimentClient(processor);

        // bucketed but outside the rollout
        assertEquals("control",
                ((Flag) client.getExperimentFlag("pricing_page", "user-1")).getVariant());
        // no metadata at all
        assertNull(((Flag) client.getExperimentFlag("some_feature", "user-1")).getExperiment());
        // enrolled, but the flag is off
        assertEquals(Boolean.FALSE,
                client.getExperimentFlag("disabled_feature", "user-1").getEnabled());

        verify(processor, never()).trackExposureEvent(any(), any(), any(), any(), any());
    }

    private static Stream<Arguments> experimentFlagFallbacks() {
        return Stream.of(
                Arguments.of("no_such_feature", false, FeatureNotFoundError.class),
                Arguments.of("checkout_cta", true, FlagsmithApiError.class));
    }

    @ParameterizedTest
    @MethodSource("experimentFlagFallbacks")
    public void testGetExperimentFlagFallsBackToTheDefaultHandlerWithoutAnExposure(
            String featureName, boolean flagsUnavailable, Class<? extends Exception> noDefaultError)
            throws FlagsmithClientError {
        EventProcessor processor = mock(EventProcessor.class);

        BaseFlag flag = experimentClient(processor, true, flagsUnavailable)
                .getExperimentFlag(featureName, "user-1");
        assertTrue(flag instanceof DefaultFlag);
        assertEquals(DEFAULT_FLAG_VALUE, flag.getValue());

        FlagsmithClient noDefault = experimentClient(processor, false, flagsUnavailable);
        assertThrows(noDefaultError, () -> noDefault.getExperimentFlag(featureName, "user-1"));
        verify(processor, never()).trackExposureEvent(any(), any(), any(), any(), any());
    }

    @Test
    public void testGetExperimentFlagRecordsNoExposureWithLocalEvaluation()
            throws JsonProcessingException, FlagsmithClientError {
        String baseUrl = "http://bad-url";
        MockInterceptor interceptor = new MockInterceptor();
        EventProcessor processor = mock(EventProcessor.class);
        interceptor.addRule()
                .get(baseUrl + "/environment-document/")
                .anyTimes()
                .respond(
                        MapperFactory.getMapper()
                                .writeValueAsString(FlagsmithTestHelper.environmentModel()),
                        MEDIATYPE_JSON);

        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(FlagsmithConfig.newBuilder()
                        .baseUri(baseUrl)
                        .addHttpInterceptor(interceptor)
                        .withEventProcessor(processor)
                        .withLocalEvaluation(true)
                        .build())
                .setApiKey("ser.abcdefg")
                .build();
        client.updateEnvironment();

        BaseFlag flag = client.getExperimentFlag("some_feature", "user-1");

        assertTrue(flag instanceof Flag);
        assertNull(((Flag) flag).getVariant());
        assertNull(((Flag) flag).getExperiment());
        verify(processor, never()).trackExposureEvent(any(), any(), any(), any(), any());
    }

    /**
     * An events-enabled config serving the experiment identity flags, which records each events
     * batch as "environment key|body".
     */
    private static FlagsmithConfig.Builder recordingEventsConfig(List<String> batches) {
        MockInterceptor interceptor = new MockInterceptor();
        interceptor.addRule()
                .post("http://bad-url/identities/")
                .anyTimes()
                .respond(FlagsmithTestHelper.getIdentitiesFlagsWithExperiment(), MEDIATYPE_JSON);
        interceptor.addRule()
                .post("http://events-uri/v1/events")
                .headerMatches("Flagsmith-SDK-User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
                .anyTimes()
                .respond("{\"accepted\": 1, \"rejected\": []}", MEDIATYPE_JSON);
        // Added before the MockInterceptor, which short-circuits the chain once it matches.
        return FlagsmithConfig.newBuilder()
                .baseUri("http://bad-url")
                .addHttpInterceptor((chain) -> {
                    Request request = chain.request();
                    if (request.url().toString().endsWith("/v1/events")) {
                        Buffer buffer = new Buffer();
                        request.body().writeTo(buffer);
                        batches.add(request.header("X-Environment-Key") + "|" + buffer.readUtf8());
                    }
                    return chain.proceed(request);
                })
                .addHttpInterceptor(interceptor)
                .eventsUri("http://events-uri")
                .withEnableEvents(Boolean.TRUE)
                .withEventsFlushIntervalMillis(0);
    }

    @Test
    public void testCloseFlushesBufferedEvents() throws FlagsmithClientError, IOException {
        List<String> batches = Collections.synchronizedList(new ArrayList<>());
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(recordingEventsConfig(batches).build())
                .setApiKey("api-key")
                .build();

        client.getExperimentFlag("checkout_cta", "user-1");
        assertTrue(batches.isEmpty());

        client.close();

        assertEquals(1, batches.size());
        String body = batches.get(0).substring(batches.get(0).indexOf('|') + 1);
        JsonNode events = MapperFactory.getMapper().readTree(body).get("events");
        assertEquals(1, events.size());
        assertEquals("$flag_exposure", events.get(0).get("event").asText());
        assertEquals("checkout_cta", events.get(0).get("feature_name").asText());
        assertEquals("user-1", events.get(0).get("identifier").asText());
        assertEquals("treatment", events.get(0).get("value").asText());
        assertEquals(42, events.get(0).get("metadata").get("experiment_id").asInt());
    }

    @Test
    public void testRebuildingClosesThePreviousEventProcessor() {
        List<String> batches = Collections.synchronizedList(new ArrayList<>());
        FlagsmithClient.Builder builder = FlagsmithClient.newBuilder()
                .withConfiguration(recordingEventsConfig(batches).build())
                .setApiKey("api-key");
        FlagsmithClient client = builder.build();
        client.trackEvent("purchase", "user-1");

        builder.build();
        assertEquals(1, batches.size());

        client.trackEvent("purchase", "user-2");
        client.close();
        assertEquals(2, batches.size());
    }

    @Test
    public void testEventsRequestLeavesOutCustomHeaders() {
        List<Request> requests = Collections.synchronizedList(new ArrayList<>());
        MockInterceptor interceptor = new MockInterceptor();
        interceptor.addRule()
                .post("http://events-uri/v1/events")
                .anyTimes()
                .respond("{\"accepted\": 1, \"rejected\": []}", MEDIATYPE_JSON);
        HashMap<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Authorization", "Bearer flags-api-only");
        customHeaders.put("X-Environment-Key", "flags-api-only-key");
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withConfiguration(FlagsmithConfig.newBuilder()
                        .baseUri("http://bad-url")
                        .addHttpInterceptor((chain) -> {
                            requests.add(chain.request());
                            return chain.proceed(chain.request());
                        })
                        .addHttpInterceptor(interceptor)
                        .eventsUri("http://events-uri")
                        .withEnableEvents(Boolean.TRUE)
                        .withEventsFlushIntervalMillis(0)
                        .build())
                .withCustomHttpHeaders(customHeaders)
                .setApiKey("api-key")
                .build();

        client.trackEvent("purchase", "user-1");
        client.close();

        assertEquals(1, requests.size());
        assertNull(requests.get(0).header("Authorization"));
        assertEquals(Collections.singletonList("api-key"),
                requests.get(0).headers("X-Environment-Key"));
        assertTrue(requests.get(0).header("User-Agent").startsWith("flagsmith-java-sdk/"));
    }

    @Test
    public void testClientsSharingAConfigSendEventsUnderTheirOwnKeys() throws Exception {
        List<String> batches = Collections.synchronizedList(new ArrayList<>());
        FlagsmithConfig config = recordingEventsConfig(batches).build();
        FlagsmithClient clientA = FlagsmithClient.newBuilder()
                .withConfiguration(config).setApiKey("key-a").build();
        FlagsmithClient clientB = FlagsmithClient.newBuilder()
                .withConfiguration(config).setApiKey("key-b").build();

        assertNotSame(clientA.getEventProcessor(), clientB.getEventProcessor());

        clientA.trackEvent("purchase", "user-a");
        clientB.trackEvent("purchase", "user-b");
        clientA.flushEvents().get(5, TimeUnit.SECONDS);
        clientB.flushEvents().get(5, TimeUnit.SECONDS);

        assertEquals(2, batches.size());
        assertTrue(batches.get(0).startsWith("key-a|"));
        assertTrue(batches.get(0).contains("user-a") && !batches.get(0).contains("user-b"));
        assertTrue(batches.get(1).startsWith("key-b|"));
        assertTrue(batches.get(1).contains("user-b") && !batches.get(1).contains("user-a"));
        clientA.close();
        clientB.close();
    }

    @Test
    public void testClosingOneClientLeavesAnotherOnTheSameConfigTracking() throws Exception {
        List<String> batches = Collections.synchronizedList(new ArrayList<>());
        FlagsmithConfig config = recordingEventsConfig(batches).build();
        FlagsmithClient clientA = FlagsmithClient.newBuilder()
                .withConfiguration(config).setApiKey("key-a").build();
        FlagsmithClient clientB = FlagsmithClient.newBuilder()
                .withConfiguration(config).setApiKey("key-b").build();

        clientA.close();
        clientB.trackEvent("purchase", "user-b");
        clientB.flushEvents().get(5, TimeUnit.SECONDS);

        assertEquals(1, batches.size());
        assertTrue(batches.get(0).startsWith("key-b|"));
        assertTrue(batches.get(0).contains("user-b"));
        clientB.close();
    }

    @Test
    public void testCustomApiWrapperWithAnEventsConfigDeliversEvents() throws Exception {
        List<String> batches = Collections.synchronizedList(new ArrayList<>());
        FlagsmithApiWrapper wrapper = new FlagsmithApiWrapper(
                FlagsmithConfig.newBuilder().baseUri("http://bad-url").build(),
                null, new FlagsmithLogger(), "wrapper-key");
        FlagsmithClient client = FlagsmithClient.newBuilder()
                .withFlagsmithApiWrapper(wrapper)
                .withConfiguration(recordingEventsConfig(batches).build())
                .setApiKey("wrapper-key")
                .build();

        client.trackEvent("purchase", "user-1");
        client.flushEvents().get(5, TimeUnit.SECONDS);

        assertEquals(1, batches.size());
        assertTrue(batches.get(0).startsWith("wrapper-key|"));
        assertTrue(batches.get(0).contains("user-1"));
        client.close();
    }

    @Test
    public void testAnInjectedEventProcessorBacksOnlyOneClient() throws Exception {
        List<String> batches = Collections.synchronizedList(new ArrayList<>());
        FlagsmithConfig.Builder configBuilder = recordingEventsConfig(batches);
        FlagsmithConfig probe = configBuilder.build();
        FlagsmithConfig config = configBuilder
                .withEventProcessor(new EventProcessor(
                        probe.getHttpClient(), probe.getEventsUri(), 1000, 0))
                .build();
        FlagsmithClient clientA = FlagsmithClient.newBuilder()
                .withConfiguration(config).setApiKey("key-a").build();
        FlagsmithClient.Builder clientBBuilder = FlagsmithClient.newBuilder()
                .withConfiguration(config).setApiKey("key-b");

        assertThrows(FlagsmithRuntimeError.class, clientBBuilder::build);

        clientA.trackEvent("purchase", "user-a");
        clientA.flushEvents().get(5, TimeUnit.SECONDS);
        assertEquals(1, batches.size());
        assertTrue(batches.get(0).startsWith("key-a|"));
        clientA.close();
    }

    @Test
    public void testFailedClaimDoesNotStartPolling() {
        FlagsmithConfig probe = FlagsmithConfig.newBuilder().build();
        FlagsmithConfig config = FlagsmithConfig.newBuilder()
                .withLocalEvaluation(true)
                .withEventProcessor(new EventProcessor(
                        probe.getHttpClient(), probe.getEventsUri(), 1000, 0))
                .build();
        FlagsmithClient clientA = FlagsmithClient.newBuilder()
                .withConfiguration(config)
                .withPollingManager(mock(PollingManager.class))
                .setApiKey("ser.key-a")
                .build();
        PollingManager pollingB = mock(PollingManager.class);
        FlagsmithClient.Builder clientBBuilder = FlagsmithClient.newBuilder()
                .withConfiguration(config)
                .withPollingManager(pollingB)
                .setApiKey("ser.key-b");

        assertThrows(FlagsmithRuntimeError.class, clientBBuilder::build);
        verify(pollingB, never()).startPolling();
        clientA.close();
    }
}
