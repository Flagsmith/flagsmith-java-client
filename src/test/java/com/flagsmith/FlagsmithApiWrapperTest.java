package com.flagsmith;

import static com.flagsmith.FlagsmithTestHelper.flag;
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
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
    defaultConfig = FlagsmithConfig.newBuilder().addHttpInterceptor(interceptor).retries(1).baseUri(BASE_URL)
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
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags(null, true);

    // Assert
    assertEquals(newFlagsAndTraits().getFlags(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit") // problem wala
  public void getFeatureFlags_noUser_fail() {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/flags/?page=1")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags(null, false);

    // Assert
    assertEquals(new ArrayList<>(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_withUser_success() throws JsonProcessingException {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=some-user")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);

    // Act
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags("some-user", false);

    // Assert
    assertEquals(newFlagsAndTraits().getFlags(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_withUser_defaultFlags_success() throws JsonProcessingException {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=some-user")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);
    defaultConfig.getFlagsmithFlagDefaults().setDefaultFeatureFlags(new HashSet<String>() {{
      add("default-flag");
    }});

    // Act
    final List<FeatureStateModel> actualFeatureFlags = sut.getFeatureFlags("some-user", false);

    // Assert
    final FlagsAndTraits expectedFlags = newFlagsAndTraits();
    expectedFlags.getFlags().add(flag("default-flag", null, "FLAG", false, null));
    assertEquals(expectedFlags.getFlags(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_success() throws JsonProcessingException {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=ident")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getUserFlagsAndTraits("ident", true);

    // Assert
    assertEquals(newFlagsAndTraits(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit") // problem wala
  public void getUserFlagsAndTraits_fail() {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=ident")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getUserFlagsAndTraits("ident", false);

    // Assert
    assertEquals(getEmptyFlagsAndTraits(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getUserFlagsAndTraits_defaultFlags_success() throws JsonProcessingException {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/identities/?identifier=ident")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);
    defaultConfig.getFlagsmithFlagDefaults().setDefaultFeatureFlags(new HashSet<String>() {{
      add("default-flag");
    }});

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.getUserFlagsAndTraits("ident", true);

    // Assert
    final FlagsAndTraits expectedFlags = newFlagsAndTraits();
    expectedFlags.getFlags().add(flag("default-flag", null, "FLAG", false, null));
    assertEquals(expectedFlags, actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_success() throws JsonProcessingException {
    // Arrange
    final List<TraitModel> traits = new ArrayList<TraitModel>(Arrays.asList(new TraitRequest()));
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.identifyUserWithTraits("user-w-traits", traits, true);

    // Assert
    assertEquals(newFlagsAndTraits(), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit") // problem wala
  public void identifyUserWithTraits_fail() {
    // Arrange
    final List<TraitModel> traits = new ArrayList<TraitModel>(Arrays.asList(new TraitRequest()));
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.identifyUserWithTraits("user-w-traits", traits, false);

    // Assert
    assertEquals(getEmptyFlagsAndTraits(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_defaultFlags_success() throws JsonProcessingException {
    // Arrange
    final List<TraitModel> traits = new ArrayList<TraitModel>(Arrays.asList(new TraitRequest()));
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(mapper.writeValueAsString(newFlagsAndTraits()), MEDIATYPE_JSON);
    defaultConfig.getFlagsmithFlagDefaults().setDefaultFeatureFlags(new HashSet<String>() {{
      add("default-flag");
    }});

    // Act
    final FlagsAndTraits actualFeatureFlags = sut.identifyUserWithTraits("user-w-traits", traits, true);

    // Assert
    final FlagsAndTraits expectedFlags = newFlagsAndTraits();
    expectedFlags.getFlags().add(flag("default-flag", null, "FLAG", false, null));
    assertEquals(expectedFlags, actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void postUserTraits_success() throws JsonProcessingException {
    // Arrange
    final TraitRequest inputTrait = new TraitRequest();
    final TraitRequest expectedTrait = new TraitRequest();
    expectedTrait.setValue("some-value");
    IdentityModel identityModel = new IdentityModel();
    identityModel.setIdentifier("username");
    expectedTrait.setIdentity(identityModel);
    interceptor.addRule()
        .post(BASE_URL + "/traits/")
        .respond(mapper.writeValueAsString(expectedTrait), MEDIATYPE_JSON);

    // Act
    final TraitRequest actualTrait = sut.postUserTraits("username", inputTrait, true);

    // Assert
    assertEquals(expectedTrait, actualTrait);
    assertEquals("username", actualTrait.getIdentity().getIdentifier());
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void postUserTraits_fail() {
    // Arrange
    interceptor.addRule()
        .post(BASE_URL + "/traits/")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final TraitModel actualTrait = sut.postUserTraits("username", new TraitRequest(), false);

    // Assert
    assertNull(actualTrait);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  private FlagsAndTraits newFlagsAndTraits() {
    final FeatureModel feature = new FeatureModel();
    feature.setName("my-test-flag");
    final FeatureStateModel flag = new FeatureStateModel();
    flag.setFeature(feature);
    final List<FeatureStateModel> flags = new ArrayList<>();
    flags.add(flag);

    return getEmptyFlagsAndTraits(flags);
  }

  private FlagsAndTraits getEmptyFlagsAndTraits(List<FeatureStateModel> flags) {
    final FlagsAndTraits flagsAndTraits = new FlagsAndTraits();
    flagsAndTraits.setFlags(flags);
    flagsAndTraits.setTraits(new ArrayList<>());
    return flagsAndTraits;
  }
}
