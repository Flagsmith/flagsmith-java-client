package com.solidstategroup.bullettrain;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and
 * features ids etc as required.
 */
public class BulletTrainClientTest {

    private static final String API_KEY = "S28U5Jhyp9Jmy6QX8FQtgS";
    BulletTrainClient bulletClient;

    @BeforeTest
    public void init() {
        bulletClient = BulletTrainClient.newBuilder()
                .setApiKey(API_KEY)
                .build();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_Then_Success() {
        List<Flag> featureFlags = bulletClient.getFeatureFlags();

        assertNotNull(featureFlags, "Should feature flags back");
        assertTrue(featureFlags.size() > 0, "Should have test featureFlags back");

        for (Flag flag : featureFlags) {
            assertNotNull(flag.getFeature(), "Flag should have feature");
        }
    }

    @Ignore(value = "requires specific features enabled and exist per env")
    @Test(groups = "integration")
    public void testClient_When_Has_Feature_Then_Success() {
        // This will return false
        boolean featureEnabled = bulletClient.hasFeatureFlag("flag_feature");

        assertTrue(featureEnabled, "Should have test featureFlags back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_For_User_Then_Success() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("bullet_train_sample_user");

        List<Flag> featureFlags = bulletClient.getFeatureFlags(user);

        assertNotNull(featureFlags, "Should have feature flags back");
        assertTrue(featureFlags.size() > 0, "Should have test featureFlags back");

        for (Flag flag : featureFlags) {
            assertNotNull(flag.getFeature(), "Flag should have feature");
        }
    }


    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_Then_Success() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        List<Trait> userTraits = bulletClient.getTraits(user);

        assertNotNull(userTraits, "Should have user traits back");
        assertTrue(userTraits.size() > 0, "Should have test featureFlags back");

        for (Trait trait : userTraits) {
            assertNotNull(trait.getValue(), "Flag should have value for trait");
        }
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Keys_Then_Success() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        List<Trait> userTraits = bulletClient.getTraits(user, "cookies_key");

        assertNotNull(userTraits, "Should have user traits back");
        assertTrue(userTraits.size() == 1, "Should have test featureFlags back");

        for (Trait trait : userTraits) {
            assertNotNull(trait.getValue(), "Flag should have value for trait");
        }
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Empty() {
        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("invalid_users_another_user");

        List<Trait> userTraits = bulletClient.getTraits(user);

        assertNotNull(userTraits, "Should have user traits back");
        assertTrue(userTraits.size() == 0, "Should have user traits back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Then_Success() {
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");
        bulletClient.setUserTrait(user, "cookies_key", "cookies_value");

        Trait userTrait = bulletClient.getTrait(user, "cookies_key");

        assertNotNull(userTrait, "Should have user traits back");
        assertNotNull(userTrait.getValue(), "Should have user traits value");
    }


    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Update_Then_Updated() {
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        Trait userTrait = bulletClient.getTrait(user, "cookies_key");
        assertNotNull(userTrait, "Should have user traits back");
        assertNotNull(userTrait.getValue(), "Should have user traits value");

        userTrait.setValue("new value");
        Trait updated = bulletClient.updateTrait(user, userTrait);
        assertNotNull(updated, "Should have updated user traits back");
        assertTrue(updated.getValue().equals("new value"));
    }

}
