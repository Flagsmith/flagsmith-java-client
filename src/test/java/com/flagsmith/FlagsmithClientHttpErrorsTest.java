package com.flagsmith;

import java.util.Collections;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class FlagsmithClientHttpErrorsTest {

    private static final String API_KEY = "bad-key";
    private static final HashMap<String, String> customHeaders = new HashMap(){{
        put("x-custom-header", "value1");
        put("x-my-key", "value2");
    }};
    FlagsmithClient bulletClient;

    @BeforeTest
    public void init() {
        bulletClient = FlagsmithClient.newBuilder()
                .setApiKey(API_KEY)
                .withApiUrl("http://bad-url")
                .withCustomHttpHeaders(customHeaders)
                .enableLogging(FlagsmithLoggerLevel.INFO)
                .build();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_Then_Empty() {
        List<Flag> featureFlags = bulletClient.getFeatureFlags();

        assertNotNull(featureFlags, "Should feature flags back");
        assertTrue(featureFlags.isEmpty(), "Should not have test featureFlags back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_For_User_Then_Empty() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("bullet_train_sample_user");

        List<Flag> featureFlags = bulletClient.getFeatureFlags(user);

        assertNotNull(featureFlags, "Should have feature flags back");
        assertTrue(featureFlags.isEmpty(), "Should not have test featureFlags back");
    }


    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_Then_Null() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        List<Trait> userTraits = bulletClient.getTraits(user);

        assertNull(userTraits, "Should have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Keys_Then_Null() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        List<Trait> userTraits = bulletClient.getTraits(user, "cookies_key");

        assertNull(userTraits, "Should have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Null() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("invalid_users_another_user");

        List<Trait> userTraits = bulletClient.getTraits(user);

        assertNull(userTraits, "Should have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Null_Lists() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);

        assertNotNull(userFlagsAndTraits, "Should have user traits and flags back");
        assertNull(userFlagsAndTraits.getFlags(), "Should not have user flags back");
        assertNull(userFlagsAndTraits.getTraits(), "Should not have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Null_Lists() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("invalid_users_another_user");

        FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);

        assertNotNull(userFlagsAndTraits, "Should have user traits and flags back, not null");
        assertNull(userFlagsAndTraits.getFlags(), "Should not have user flags back");
        assertNull(userFlagsAndTraits.getTraits(), "Should not have no user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Null() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);

        Trait userTrait = FlagsmithClient.getTrait(userFlagsAndTraits, "cookies_key");

        assertNull(userTrait, "Should not have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Null() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);

        List<Trait> traits = FlagsmithClient.getTraits(userFlagsAndTraits, "cookies_key");

        assertNull(traits, "Should not have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Null() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);

        String featureFlagValue = FlagsmithClient.getFeatureFlagValue("font_size", userFlagsAndTraits);

        assertNull(featureFlagValue, "Should not have feature");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_False() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);

        boolean enabled = FlagsmithClient.hasFeatureFlag("hero", userFlagsAndTraits);

        assertFalse(enabled, "Should not have feature enabled");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Then_Null() {
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        Trait userTrait = bulletClient.getTrait(user, "cookies_key");

        assertNull(userTrait, "Should not have user traits back");
    }


    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Update_Then_Null() {
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        Trait userTrait = new Trait();
        userTrait.setIdentity(user);
        userTrait.setKey("some_trait");
        userTrait.setValue("new value");
        Trait updated = bulletClient.updateTrait(user, userTrait);
        assertNull(updated, "Should not have updated user traits back");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed() {
        // Given traits and no user Identity
        Trait trait1 = new Trait();
        trait1.setKey("trait_1");
        trait1.setValue("some value1");

        // When
        List<Trait> traits = bulletClient.identifyUserWithTraits(null, Collections.singletonList(trait1));

        // Then
        // nothing return and exception thrown
        assertTrue(traits.size() == 0, "Should not return any traits");
    }

    @Test(groups = "integration")
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
        List<Trait> traits = bulletClient.identifyUserWithTraits(user,  Arrays.asList(trait1, trait2));

        // Then
        assertTrue(traits.isEmpty(), "Should not have traits returned");
    }
}
