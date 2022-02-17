package com.flagsmith;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.flagsmith.config.FlagsmithCacheConfig;
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
    List<Flag> featureFlags = flagsmithClient.getFeatureFlags();

    assertNotNull(featureFlags, "Should feature flags back");
    assertTrue(featureFlags.isEmpty(), "Should not have test featureFlags back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_Features_For_User_Then_Empty() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("bullet_train_sample_user");

    List<Flag> featureFlags = flagsmithClient.getFeatureFlags(user);

    assertNotNull(featureFlags, "Should have feature flags back");
    assertTrue(featureFlags.isEmpty(), "Should not have test featureFlags back");
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_Features_For_User_Then_Throw() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("bullet_train_sample_user");

    flagsmithClient.getFeatureFlags(user, true);
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_Then_Null() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    List<Trait> userTraits = flagsmithClient.getTraits(user);

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_For_Keys_Then_Null() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    List<Trait> userTraits = flagsmithClient.getTraits(user, "cookies_key");

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Null() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("invalid_users_another_user");

    List<Trait> userTraits = flagsmithClient.getTraits(user);

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Null_Lists() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits(user);

    assertNotNull(userFlagsAndTraits, "Should have user traits and flags back, not null");
    assertNotNull(userFlagsAndTraits.getFlags(), "Should not have null user flags back");
    assertTrue(userFlagsAndTraits.getFlags().isEmpty(), "Should not have user flags back");
    assertNotNull(userFlagsAndTraits.getTraits(), "Should not have null user traits back");
    assertTrue(userFlagsAndTraits.getTraits().isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Null_Lists() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("invalid_users_another_user");

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits(user);

    assertNotNull(userFlagsAndTraits, "Should have user traits and flags back, not null");
    assertNotNull(userFlagsAndTraits.getFlags(), "Should not have null user flags back");
    assertTrue(userFlagsAndTraits.getFlags().isEmpty(), "Should not have user flags back");
    assertNotNull(userFlagsAndTraits.getTraits(), "Should not have null user traits back");
    assertTrue(userFlagsAndTraits.getTraits().isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_User_Traits_And_Flags_Then_Throw() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    flagsmithClient.getUserFlagsAndTraits(user, true);
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_User_Traits_And_Flags_Then_Throw_evenIfLoggingDisabled() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    flagsmithClient = FlagsmithClient.newBuilder()
        .setApiKey(API_KEY)
        .withApiUrl("http://bad-url")
        .withCustomHttpHeaders(customHeaders)
        .build();

    flagsmithClient.getUserFlagsAndTraits(user, true);
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Cached_Get_User_Traits_And_Flags_Then_Throw() {
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    flagsmithClient = FlagsmithClient.newBuilder()
        .setApiKey(API_KEY)
        .withApiUrl("http://bad-url")
        .withCache(FlagsmithCacheConfig.newBuilder().build())
        .build();

    flagsmithClient.getUserFlagsAndTraits(user, true);
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Null() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits(user);

    Trait userTrait = flagsmithClient.getTrait(userFlagsAndTraits, "cookies_key");

    assertNull(userTrait, "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Null() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits(user);

    List<Trait> userTraits = flagsmithClient.getTraits(userFlagsAndTraits, "cookies_key");

    assertNotNull(userTraits, "Should not have null user traits back");
    assertTrue(userTraits.isEmpty(), "Should not have user traits back");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Null() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits(user);

    String featureFlagValue = flagsmithClient.getFeatureFlagValue("font_size", userFlagsAndTraits);

    assertNull(featureFlagValue, "Should not have feature");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_False() {
    // context user
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    FlagsAndTraits userFlagsAndTraits = flagsmithClient.getUserFlagsAndTraits(user);

    boolean enabled = flagsmithClient.hasFeatureFlag("hero", userFlagsAndTraits);

    assertFalse(enabled, "Should not have feature enabled");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Trait_Then_Null() {
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    Trait userTrait = flagsmithClient.getTrait(user, "cookies_key");

    assertNull(userTrait, "Should not have user traits back");
  }


  @Test(groups = "integration-offline")
  public void testClient_When_Get_User_Trait_Update_Then_Null() {
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    Trait userTrait = new Trait();
    userTrait.setIdentity(user);
    userTrait.setKey("some_trait");
    userTrait.setValue("new value");
    Trait updated = flagsmithClient.updateTrait(user, userTrait);
    assertNull(updated, "Should not have updated user traits back");
  }


  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Get_User_Trait_Update_Then_Throw() {
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    Trait userTrait = new Trait();
    userTrait.setIdentity(user);
    userTrait.setKey("some_trait");
    userTrait.setValue("new value");
    flagsmithClient.updateTrait(user, userTrait, true);
  }

  @Test(expectedExceptions = IllegalArgumentException.class, groups = "integration-offline")
  public void testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed() {
    // Given traits and no user Identity
    Trait trait1 = new Trait();
    trait1.setKey("trait_1");
    trait1.setValue("some value1");

    // When
    List<Trait> traits = flagsmithClient
        .identifyUserWithTraits(null, Collections.singletonList(trait1)).getTraits();

    // Then
    // nothing return and exception thrown
    assertTrue(traits.size() == 0, "Should not return any traits");
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Add_Traits_For_Identity_Then_Success() {
    // Given
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    Trait trait1 = new Trait();
    trait1.setKey("trait_1");
    trait1.setValue("some value1");

    Trait trait2 = new Trait();
    trait2.setKey("trait_2");
    trait2.setValue("some value2");

    // When
    List<Trait> traits = flagsmithClient.identifyUserWithTraits(user, Arrays.asList(trait1, trait2))
        .getTraits();

    // Then
    assertTrue(traits.isEmpty(), "Should not have traits returned");
  }

  @Test(groups = "integration-offline", expectedExceptions = FlagsmithException.class)
  public void testClient_When_Add_Traits_For_Identity_Then_Throw() {
    // Given
    FeatureUser user = new FeatureUser();
    user.setIdentifier("another_user");

    Trait trait1 = new Trait();
    trait1.setKey("trait_1");
    trait1.setValue("some value1");

    // When
    flagsmithClient.identifyUserWithTraits(user, Arrays.asList(trait1), true);
  }

  @Test(groups = "integration-offline")
  public void testClient_When_Cache_Disabled_Return_Null() {
    FlagsmithCache cache = flagsmithClient.getCache();

    Assert.assertNull(cache);
  }
}
