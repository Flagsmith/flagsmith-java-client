package com.flagsmith;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class FlagsmithApiWrapperTest {

  private final String API_KEY = "OUR_API_KEY";
  private final String BASE_URL = "https://unit-test.com";
  private final ObjectMapper mapper = MapperFactory.getMappper();
  private FlagsmithApiWrapper sut;
  private FlagsmithLogger flagsmithLogger;
  private FlagsmithConfig defaultConfig;
  private MockInterceptor interceptor;

  @BeforeMethod(groups = "unit")
  public void init() {
    flagsmithLogger = mock(FlagsmithLogger.class);
    doThrow(new FlagsmithException("error Response")).when(flagsmithLogger)
        .httpError(any(), any(Response.class), eq(true));
    doThrow(new FlagsmithException("error IOException")).when(flagsmithLogger)
        .httpError(any(), any(IOException.class), eq(true));

    interceptor = new MockInterceptor();
    defaultConfig = FlagsmithConfig.newBuilder().addHttpInterceptor(interceptor).baseURI(BASE_URL)
        .build();
    sut = new FlagsmithApiWrapper(defaultConfig, null, flagsmithLogger, API_KEY);
  }

  @Test(groups = "unit")
  public void getFeatureFlags_noUser_success() throws JsonProcessingException {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/flags/?page=1")
        .respond(mapper.writeValueAsString(newFlagsAndTraits().getFlags()), MEDIATYPE_JSON);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    assertEquals(newFlagsAndTraits(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_noUser_fail() {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/flags/?page=1")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(null, false);

    // Assert
    assertEquals(getEmptyFlagsAndTraits(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_withUser_success() throws JsonProcessingException {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("some-user");
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=some-user")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getFeatureFlags(user, false);

    // Assert
    assertEquals(newFlagsAndTraits(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_success() throws JsonProcessingException {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("ident");
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=ident")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getUserFlagsAndTraits(user, true);

    // Assert
    assertEquals(newFlagsAndTraits(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_fail() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("ident");
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=ident")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getUserFlagsAndTraits(user, false);

    // Assert
    assertEquals(getEmptyFlagsAndTraits(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_success() throws JsonProcessingException {
    // Arrange
    final List<Trait> traits = new ArrayList<Trait>(Arrays.asList(new Trait()));
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("user-w-traits");
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.identifyUserWithTraits(user, traits, true);

    // Assert
    assertEquals(newFlagsAndTraits(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fail() {
    // Arrange
    final List<Trait> traits = new ArrayList<Trait>(Arrays.asList(new Trait()));
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("user-w-traits");
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.identifyUserWithTraits(user, traits, false);

    // Assert
    assertEquals(getEmptyFlagsAndTraits(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void postUserTraits_success() throws JsonProcessingException {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("username");
    final Trait inputTrait = new Trait();
    final Trait expectedTrait = new Trait();
    expectedTrait.setValue("some-value");
    interceptor.addRule()
        .post(BASE_URL + "/traits/")
        .respond(mapper.writeValueAsString(expectedTrait), MEDIATYPE_JSON);

    // Act
    final Trait actualTrait = sut.postUserTraits(user, inputTrait, true);

    // Assert
    assertEquals(expectedTrait, actualTrait);
    assertEquals(user.getIdentifier(), inputTrait.getIdentity().getIdentifier());
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void postUserTraits_fail() {
    // Arrange
    final FeatureUser user = new FeatureUser();
    user.setIdentifier("username");
    interceptor.addRule()
        .post(BASE_URL + "/traits/")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final Trait actualTrait = sut.postUserTraits(user, new Trait(), false);

    // Assert
    assertNull(actualTrait);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  private FlagsAndTraits newFlagsAndTraits() {
    final Feature feature = new Feature();
    feature.setName("my-test-flag");
    final Flag flag = new Flag();
    flag.setFeature(feature);
    final List<Flag> flags = new ArrayList<>();
    flags.add(flag);

    return getEmptyFlagsAndTraits(flags);
  }

  private FlagsAndTraits getEmptyFlagsAndTraits(List<Flag> flags) {
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    flagsAndTraits.setFlags(flags);
    flagsAndTraits.setTraits(new ArrayList<>());
    return flagsAndTraits;
  }
}
