package com.ssg.bullettrain;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 *
 */
public class BulletTrainClientTest {

    private static final String API_KEY = "[myapi_key]";

    @Test(groups = "integration")
    public void testClient_When_Get_Features_Then_Success() {

        BulletTrainClient bulletClient = BulletTrainClient.newBuilder()
                .setApiKey(API_KEY)
                .build();

        List<FeatureFlag> featureFlags = bulletClient.getFeatureFlags();

        assertNotNull(featureFlags, "Should feature flags back");
        assertTrue(bulletClient.getFeatureFlags().size() > 0, "Should have test featureFlags back");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_For_User_Then_Success() {

        BulletTrainClient bulletClient = BulletTrainClient.newBuilder()
                .setApiKey(API_KEY)
                .build();

        User user = new User();
        user.setIdentifier("bullet_train_sample_user");

        List<FeatureFlag> featureFlags = bulletClient.getFeatureFlags(user);

        assertNotNull(featureFlags, "Should feature flags back");
        assertTrue(bulletClient.getFeatureFlags().size() > 0, "Should have test featureFlags back");
    }

}
