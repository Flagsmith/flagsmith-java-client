package com.solidstategroup.bullettrain;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and
 * features ids etc as required.
 */
public class BulletTrainClientTest {

    private static final String API_KEY = "QjgYur4LQTwe5HpvbvhpzK";
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

    @Test(expectedExceptions = IllegalArgumentException.class, groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed() {
        // Given traits and no user Identity
        Trait trait1 = new Trait();
        trait1.setKey("trait_1");
        trait1.setValue("some value1");

        // When
        List<Trait> traits = bulletClient.addTraitsForIdentity(null,  Arrays.asList(trait1));

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
        List<Trait> traits = bulletClient.addTraitsForIdentity(user,  Arrays.asList(trait1, trait2));

        // Then
        assertTrue(traits.size() == 2, "Should have 2 traits returned");

        assertTrue(trait1.getKey().equals(traits.get(0).getKey()));
        assertTrue(trait1.getValue().equals(traits.get(0).getValue()));

        assertTrue(trait2.getKey().equals(traits.get(1).getKey()));
        assertTrue(trait2.getValue().equals(traits.get(1).getValue()));
    }

    @Test(groups = "integration")
    public void testClient_When_Remove_Traits_For_Identity_Then_Success() {
        // Given identity and user traits
        FeatureUser user = new FeatureUser();
        user.setIdentifier("another_user");

        Trait trait1 = new Trait();
        trait1.setKey("trait_1");
        trait1.setValue("some value1");

        Trait trait2 = new Trait();
        trait2.setKey("trait_2");
        trait2.setValue("some value2");

        // Create Traits
        List<Trait> traits = bulletClient.addTraitsForIdentity(user,  Arrays.asList(trait1, trait2));
        assertTrue(traits.size() == 2, "Should have 2 traits returned");

        // When
        // update with null or empty should delete all traits
        List<Trait> overrideTraits = bulletClient.addTraitsForIdentity(user,  null);

        // Then
        assertTrue(overrideTraits.size() == 0, "Should have returned no traits");
    }

}
