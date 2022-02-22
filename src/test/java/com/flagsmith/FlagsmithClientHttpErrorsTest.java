package com.flagsmith;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FlagsmithClientHttpErrorsTest {

  private static final String API_KEY = "bad-key";
  private static final HashMap<String, String> customHeaders = new HashMap() {{
    put("x-custom-header", "value1");
    put("x-my-key", "value2");
  }};
  FlagsmithClient flagsmithClient;

  @BeforeMethod(groups = "integration-offline")
  public void init() {
    flagsmithClient = FlagsmithClient.newBuilder()
        .setApiKey(API_KEY)
        .withApiUrl("http://bad-url")
        .withCustomHttpHeaders(customHeaders)
        .enableLogging(FlagsmithLoggerLevel.INFO)
        .build();
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_Features_Then_Empty() {
    List<FeatureStateModel> featureFlags = flagsmithClient.getFeatureFlags();

    assertNotNull(featureFlags, "Should feature flags back");
    assertTrue(featureFlags.isEmpty(), "Should not have test featureFlags back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_Features_For_User_Then_Empty() {
    // context user
    List<FeatureStateModel> featureFlags = flagsmithClient.getFeatureFlags("bullet_train_sample_user");

    assertNotNull(featureFlags, "Should have feature flags back");
    assertTrue(featureFlags.isEmpty(), "Should not have test featureFlags back");
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_Features_For_User_Then_Throw() {
    // context user
    flagsmithClient.getFeatureFlags("bullet_train_sample_user", true);
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_Then_Null() {
    // context user
    List<TraitModel> userTraits = flagsmithClient.getTraits("another_user");

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_For_Keys_Then_Null() {
    // context user
    List<TraitModel> userTraits = flagsmithClient.getTraits("another_user", "cookies_key");

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Null() {
    // context user
    List<TraitModel> userTraits = flagsmithClient.getTraits("invalid_users_another_user");

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Null_Lists() {
    // context user

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits("another_user");

    assertNotNull(userFlagsAndTraits, "Should have user traits and flags back, not null");
    assertNotNull(userFlagsAndTraits.getFlags(), "Should not have null user flags back");
    assertTrue(userFlagsAndTraits.getFlags().isEmpty(), "Should not have user flags back");
    assertNotNull(userFlagsAndTraits.getTraits(), "Should not have null user traits back");
    assertTrue(userFlagsAndTraits.getTraits().isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Null_Lists() {
    // context user
    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits("invalid_users_another_user");

    assertNotNull(userFlagsAndTraits, "Should have user traits and flags back, not null");
    assertNotNull(userFlagsAndTraits.getFlags(), "Should not have null user flags back");
    assertTrue(userFlagsAndTraits.getFlags().isEmpty(), "Should not have user flags back");
    assertNotNull(userFlagsAndTraits.getTraits(), "Should not have null user traits back");
    assertTrue(userFlagsAndTraits.getTraits().isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_User_Traits_And_Flags_Then_Throw() {
    // context user
    flagsmithClient.getUserFlagsAndTraits("another_user", true);
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_User_Traits_And_Flags_Then_Throw_evenIfLoggingDisabled() {
    // context user
    flagsmithClient = FlagsmithClient.newBuilder()
        .setApiKey(API_KEY)
        .withApiUrl("http://bad-url")
        .withCustomHttpHeaders(customHeaders)
        .build();

    flagsmithClient.getUserFlagsAndTraits("another_user", true);
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Cached_Get_User_Traits_And_Flags_Then_Throw() {
    flagsmithClient = FlagsmithClient.newBuilder()
        .setApiKey(API_KEY)
        .withApiUrl("http://bad-url")
        .withCache(FlagsmithCacheConfig.newBuilder().build())
        .build();

    flagsmithClient.getUserFlagsAndTraits("another_user", true);
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Null() {
    // context user
    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits("another_user");

    TraitModel userTrait = flagsmithClient.getTrait(userFlagsAndTraits, "cookies_key");

    assertNull(userTrait, "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Null() {
    // context user
    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits("another_user");

    List<TraitModel> userTraits = flagsmithClient.getTraits(userFlagsAndTraits, "cookies_key");

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Null() {
    // context user
    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits("another_user");

    String featureFlagValue = flagsmithClient.getFeatureFlagValue("font_size", userFlagsAndTraits);

    assertNull(featureFlagValue, "Should not have feature");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_False() {
    // context user
    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits("another_user");

    boolean enabled = flagsmithClient.hasFeatureFlag("hero", userFlagsAndTraits);

    assertFalse(enabled, "Should not have feature enabled");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Trait_Then_Null() {
    TraitModel userTrait = flagsmithClient.getTrait("another_user", "cookies_key");

    assertNull(userTrait, "Should not have user traits back");
  }


  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Trait_Update_Then_Null() {
    IdentityModel user = new IdentityModel();
    user.setIdentifier("another_user");

    TraitRequest userTrait = new TraitRequest();
    userTrait.setIdentity(user);
    userTrait.setKey("some_trait");
    userTrait.setValue("new value");
    TraitModel updated = flagsmithClient.updateTrait("another_user", userTrait);
    assertNull(updated, "Should not have updated user traits back");
  }


  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_User_Trait_Update_Then_Throw() {
    IdentityModel user = new IdentityModel();
    user.setIdentifier("another_user");

    TraitRequest userTrait = new TraitRequest();
    userTrait.setIdentity(user);
    userTrait.setKey("some_trait");
    userTrait.setValue("new value");
    flagsmithClient.updateTrait(user.getIdentifier(), userTrait, true);
  }

  @Test(expectedExceptions = IllegalArgumentException.class, groups = "integration-offline")
  public void testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed() {
    // Given traits and no user Identity
    TraitRequest trait1 = new TraitRequest();
    trait1.setKey("trait_1");
    trait1.setValue("some value1");

    // When
    List<TraitModel> traits = flagsmithClient
        .identifyUserWithTraits(null, Collections.singletonList(trait1)).getTraits();

    // Then
    // nothing return and exception thrown
    assertTrue(traits.size() == 0, "Should not return any traits");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Add_Traits_For_Identity_Then_Success() {
    // Given
    TraitRequest trait1 = new TraitRequest();
    trait1.setKey("trait_1");
    trait1.setValue("some value1");

    TraitRequest trait2 = new TraitRequest();
    trait2.setKey("trait_2");
    trait2.setValue("some value2");

    // When
    List<TraitModel> traits = flagsmithClient.identifyUserWithTraits("another_user", Arrays.asList(trait1, trait2))
        .getTraits();

    // Then
    assertTrue(traits.isEmpty(), "Should not have traits returned");
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Add_Traits_For_Identity_Then_Throw() {
    // Given
    TraitRequest trait1 = new TraitRequest();
    trait1.setKey("trait_1");
    trait1.setValue("some value1");

    // When
    flagsmithClient.identifyUserWithTraits("another_user", Arrays.asList(trait1), true);
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Cache_Disabled_Return_Null() {
    FlagsmithCache cache = flagsmithClient.getCache();

    Assert.assertNull(cache);
  }
}
