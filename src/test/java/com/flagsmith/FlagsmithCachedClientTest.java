package com.flagsmith;

import static com.flagsmith.FlagsmithTestHelper.assignTraitToUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.createFeature;
import static com.flagsmith.FlagsmithTestHelper.createProjectEnvironment;
import static com.flagsmith.FlagsmithTestHelper.createUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.flag;
import static com.flagsmith.FlagsmithTestHelper.switchFlagForUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.Flags;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "integration", enabled = false)
public class FlagsmithCachedClientTest {

  // User 2 initial trait
  private static final String user2traitKey = "foo1";
  private static final String user2traitVal = "xxx";
  private FlagsmithTestHelper.ProjectEnvironment environment;
  private FlagsmithCache clientCache;
  private int featureId;
  private int userId1;
  private int userId2;
  private int userId3;
  private String user1;
  private String user2;
  private String user3;
  private boolean user2feature1initValue = true;

  @BeforeMethod(groups = "integration")
  public void setup() {
    environment = createProjectEnvironment(
        "TEST",
        "TEST", true);

    featureId = createFeature(new FlagsmithTestHelper.FlagFeature(
        "Flag to be enabled for the user",
        null,
        environment.projectId,
        false));
    createFeature(new FlagsmithTestHelper.FlagFeature(
        "Other Flag",
        null,
        environment.projectId,
        false));

    user1 = "mr-user-1";
    user2 = "mr-user-2";
    user3 = "mr-user-3";

    userId1 = createUserIdentity(user1, environment.apiKey);
    userId2 = createUserIdentity(user2, environment.apiKey);
    userId3 = createUserIdentity(user3, environment.apiKey);

    switchFlagForUser(featureId, userId2, user2feature1initValue, environment.apiKey);

    assignTraitToUserIdentity(user2, user2traitKey, user2traitVal,
        environment.apiKey);
    assignTraitToUserIdentity("mr-user-999", "foo2", "yyy", environment.apiKey);

    clientCache = environment.client.getCache();
    assertNotNull(clientCache);
    assertEquals(0, clientCache.estimatedSize());
  }

  @AfterMethod(groups = "integration")
  public void cleanUp() {
    clientCache.invalidateAll();
    assertEquals(0, clientCache.estimatedSize());
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Stats_Disabled_Then_All_Values_Zero() {
    CacheStats cacheStats = clientCache.stats();
    assertNotNull(cacheStats);
    assertEquals(0, cacheStats.loadCount());
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_Project_Flags_Then_Not_Cached() throws FlagsmithApiError  {
    assertEquals(0, clientCache.estimatedSize());

    final Flags featureFlags = environment.client.getEnvironmentFlags();

    assertEquals(0, clientCache.estimatedSize());
    assertNotNull(featureFlags);
    assertThat(featureFlags.getFlags().values())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, false),
            flag("Other Flag", null, false)
        );
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_User_Flags_Then_Reuses_Cache_Entry() throws FlagsmithApiError {
    final Flags featureFlags1 = environment.client.getEnvironmentFlags();
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1);

    final Flags featureFlags1Again = environment.client.getEnvironmentFlags();
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1Again);
    assertEquals(featureFlags1, featureFlags1Again);
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_User_Flags_Then_Refetches_Only_If_Not_Present() throws FlagsmithApiError {
    final Flags featureFlags1 = environment.client.getEnvironmentFlags();
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1);

    createFeature(new FlagsmithTestHelper.FlagFeature(
        "a-new-flag",
        null,
        environment.projectId,
        false));

    final Flags featureFlags1Again = environment.client.getEnvironmentFlags();
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1Again);
    assertEquals(featureFlags1, featureFlags1Again);

    clientCache.invalidate(user1);

    final Flags updatedFlags1 = environment.client.getEnvironmentFlags();
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(updatedFlags1);
    assertNotEquals(featureFlags1, updatedFlags1);
    assertThat(updatedFlags1.getFlags().values())
        .hasSize(3)
        .containsExactlyInAnyOrder(
            flag("a-new-flag", null, false),
            flag("Flag to be enabled for the user", null, false),
            flag("Other Flag", null, false)
        );
  }
}
