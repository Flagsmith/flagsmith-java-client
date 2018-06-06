package com.ssg.bullettrain;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 *
 */
public class BulletTrainClientTest {

    private static final String API_KEY = "QjgYur4LQTwe5HpvbvhpzK";

    @Test(groups = "integration")
    public void testClient_When_Get_Features_Then_Success() {

        BulletTrainClient bulletClient = BulletTrainClient.newBuilder()
                .setApiKey(API_KEY)
                .build();

        List<Flag> featureFlags = bulletClient.getFeatureFlags();

        assertNotNull(featureFlags, "Should feature flags back");
        assertTrue(bulletClient.getFeatureFlags().size() > 0, "Should have test featureFlags back");

        for(Flag flag: featureFlags){
            assertNotNull(flag.getFeature(), "Flag should have feature");
        }
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_For_User_Then_Success() {

        BulletTrainClient bulletClient = BulletTrainClient.newBuilder()
                .setApiKey(API_KEY)
                .build();

        // context user
        FeatureUser user = new FeatureUser();
        user.setIdentifier("bullet_train_sample_user");

        List<Flag> featureFlags = bulletClient.getFeatureFlags(user);

        assertNotNull(featureFlags, "Should feature flags back");
        assertTrue(bulletClient.getFeatureFlags().size() > 0, "Should have test featureFlags back");

        for(Flag flag: featureFlags){
            assertNotNull(flag.getFeature(), "Flag should have feature");
        }
    }

}
