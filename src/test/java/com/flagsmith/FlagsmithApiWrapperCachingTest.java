package com.flagsmith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.Flags;
import com.flagsmith.responses.FlagsAndTraitsResponse;
import com.flagsmith.threads.RequestProcessor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.mock.MockInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

public class FlagsmithApiWrapperCachingTest {

  private final String API_KEY = "OUR_API_KEY";
  private final String BASE_URL = "https://unit-test.com";
  private FlagsmithCache flagsmithCacheImpl;
  private FlagsmithApiWrapper flagsmithAPIWrapper;
  private FlagsmithLogger flagsmithLogger;
  private Cache<String, Flags> cache;

  private FlagsmithConfig defaultConfig;
  private MockInterceptor interceptor;
  private RequestProcessor requestProcessor;

  @BeforeEach
  public void init() {
    flagsmithCacheImpl = mock(FlagsmithCacheConfig.FlagsmithCacheImpl.class);

    interceptor = new MockInterceptor();
    defaultConfig = FlagsmithConfig.newBuilder()
        .addHttpInterceptor(interceptor).retries(new Retry(1)).baseUri(BASE_URL)
        .build();

    cache = Caffeine.newBuilder().maximumSize(2).build();
    when(flagsmithCacheImpl.getCache()).thenReturn(cache);

    flagsmithLogger = mock(FlagsmithLogger.class);
    requestProcessor = mock(RequestProcessor.class);

    flagsmithAPIWrapper = new FlagsmithApiWrapper(
        flagsmithCacheImpl, defaultConfig, null, flagsmithLogger, API_KEY);
    flagsmithAPIWrapper.setRequestor(requestProcessor);
  }

  @Test
  public void getCache_returnsInternalCache() {
    final FlagsmithCache actualInternalCache = flagsmithAPIWrapper.getCache();
    assertEquals(flagsmithCacheImpl, actualInternalCache);
  }

  @Test
  public void getFeatureFlags_envFlags_cacheEnabled_dontFetchFlagsWhenInCache() {
    // Arrange
    final String cacheKey = "cacheKey";
    final Flags flagsAndTraits = new Flags();
    when(flagsmithCacheImpl.getEnvFlagsCacheKey()).thenReturn(cacheKey);
    when(flagsmithCacheImpl.getIfPresent(anyString())).thenReturn(flagsAndTraits);

    // Act
    final Flags actualFeatureFlags = flagsmithAPIWrapper.getFeatureFlags(true);

    // Assert
    verify(flagsmithCacheImpl, times(0)).getCache();
    verify(flagsmithCacheImpl, times(1)).getIfPresent(anyString());
    verify(flagsmithCacheImpl, times(2)).getEnvFlagsCacheKey();

    assertEquals(flagsAndTraits, actualFeatureFlags);
  }

  @Test
  public void getFeatureFlags_fetchFlagsFromFlagsmithAndStoreThemInCache() {
    final String cacheKey = "cacheKey";
    when(flagsmithCacheImpl.getEnvFlagsCacheKey()).thenReturn(cacheKey);
    when(flagsmithCacheImpl.getIfPresent(anyString())).thenReturn(null);
    // Arrange
    final List<FeatureStateModel> flagsAndTraits = new ArrayList<>();
    when(requestProcessor.executeAsync(any(), any(), any()))
        .thenReturn(FlagsmithTestHelper.futurableReturn(flagsAndTraits));

    // Act
    final Flags actualFeatureFlags = flagsmithAPIWrapper.getFeatureFlags(true);

    // Assert
    verify(requestProcessor, times(1)).executeAsync(any(), any(), any());
    verify(flagsmithCacheImpl, times(1)).getCache();
    assertEquals(newFlagsList(flagsAndTraits), cache.getIfPresent(cacheKey));
    assertEquals(newFlagsList(flagsAndTraits), actualFeatureFlags);
    assertEquals(1, cache.estimatedSize());
  }

  @Test
  public void getFeatureFlags_fetchFlagsFromCacheAndNotFromFlagsmith() {
    // Arrange
    final String cacheKey = "cacheKey";
    final List<FeatureStateModel> flagsAndTraits = new ArrayList<>();

    when(flagsmithCacheImpl.getIfPresent(any())).thenReturn(newFlagsList(flagsAndTraits));
    when(flagsmithCacheImpl.getEnvFlagsCacheKey()).thenReturn(cacheKey);

    // Act
    final Flags actualFeatureFlags = flagsmithAPIWrapper.getFeatureFlags(true);

    // Assert
    verify(flagsmithCacheImpl, times(0)).getCache();
    assertEquals(newFlagsList(flagsAndTraits), actualFeatureFlags);
  }

  @Test
  public void identifyUserWithTraits_nullUser() {
    // Act
    assertThrows(IllegalArgumentException.class,
        () -> flagsmithAPIWrapper.identifyUserWithTraits(null, new ArrayList<>(), false, true));

    // Assert
    verify(flagsmithCacheImpl, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test
  public void identifyUserWithTraits_nullUserIdentifier() {
    // Act
    assertThrows(IllegalArgumentException.class,
        () -> flagsmithAPIWrapper.identifyUserWithTraits("", new ArrayList<>(), false, true));

    // Assert
    verify(flagsmithCacheImpl, times(0)).getCache();
    assertEquals(0, cache.estimatedSize());
  }

  @Test
  public void identifyUserWithTraits_fetchFlagsFromFlagsmithAndStoreThemInCache_whenCacheEmpty() {
    // Arrange
    String identifier = "test-user";
    String expectedCacheKey = "identity" + identifier;
    final ArrayList<TraitModel> traits = new ArrayList<>();

    when(flagsmithCacheImpl.getIdentityFlagsCacheKey(identifier, false)).thenReturn(expectedCacheKey);

    final FlagsAndTraitsResponse flagsAndTraitsResponse = new FlagsAndTraitsResponse();
    when(requestProcessor.executeAsync(any(), any(), any()))
        .thenReturn(FlagsmithTestHelper.futurableReturn(flagsAndTraitsResponse));

    // Act
    final Flags actualUserFlagsAndTraits = flagsmithAPIWrapper.identifyUserWithTraits(
        identifier, traits, false, true);

    // Assert
    verify(requestProcessor, times(1)).executeAsync(any(), any(), any());
    assertEquals(newFlagsList(new ArrayList<>()), actualUserFlagsAndTraits);

    verify(flagsmithCacheImpl, times(1)).getIfPresent(expectedCacheKey);

    assertEquals(1, cache.estimatedSize());
    assertEquals(cache.getIfPresent(expectedCacheKey), actualUserFlagsAndTraits);
  }

  @Test
  public void testGetFeatureFlags_ReturnsFeatureFlagsFromApi_IfCacheButNoEnvCacheKey() {
    // Given
    when(flagsmithCacheImpl.getEnvFlagsCacheKey()).thenReturn(null);

    final List<FeatureStateModel> featureStateModels = new ArrayList<>();
    when(requestProcessor.executeAsync(any(), any(), any()))
        .thenReturn(FlagsmithTestHelper.futurableReturn(featureStateModels));

    // When
    Flags flags = flagsmithAPIWrapper.getFeatureFlags(false);

    // Then
    assertEquals(flags, Flags.fromFeatureStateModels(featureStateModels, null));
  }

  @Test
  public void verifyRequestBody() {
    String identifier = "test-user";
    final ArrayList<TraitModel> traits = new ArrayList<>();

    final FlagsAndTraitsResponse flagsAndTraitsResponse = new FlagsAndTraitsResponse();
    when(requestProcessor.executeAsync(any(), any(), any()))
        .thenReturn(FlagsmithTestHelper.futurableReturn(flagsAndTraitsResponse));

    final ObjectNode requestObject = MapperFactory.getMapper().createObjectNode();
    requestObject.put("identifier", identifier);
    requestObject.putPOJO("traits", traits);

    MediaType json = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(requestObject.toString(), json);
    HttpUrl url = flagsmithAPIWrapper.getConfig().getIdentitiesUri();

    // Act
    final Flags actualUserFlagsAndTraits = flagsmithAPIWrapper.identifyUserWithTraits(
        identifier, traits, false, true);

    // Assert
    ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);

    verify(requestProcessor, times(1)).executeAsync(argument.capture(), any(), anyBoolean());
    assertEquals(url, argument.getValue().url());
    assertEquals(newFlagsList(new ArrayList<>()), actualUserFlagsAndTraits);
  }

  private Flags newFlagsList(List<FeatureStateModel> flags) {
    return Flags.fromApiFlags(
        flags, null, defaultConfig.getFlagsmithFlagDefaults());
  }
}
