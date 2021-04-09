package com.flagsmith;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static com.flagsmith.FlagsmithTestHelper.assignTraitToUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.createFeature;
import static com.flagsmith.FlagsmithTestHelper.createProjectEnvironment;
import static com.flagsmith.FlagsmithTestHelper.createUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.featureUser;
import static com.flagsmith.FlagsmithTestHelper.flag;
import static com.flagsmith.FlagsmithTestHelper.switchFlagForUser;
import static com.flagsmith.FlagsmithTestHelper.trait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

@Test(groups = "integration")
public class FlagsmithCachedClientTest {

  private FlagsmithTestHelper.ProjectEnvironment environment;
  private FlagsmithCache clientCache;
  private int featureId;
  private int userId1;
  private int userId2;
  private int userId3;
  private FeatureUser user1;
  private FeatureUser user2;
  private FeatureUser user3;
  private boolean user2feature1initValue = true;

  // User 2 initial trait
  private static final String user2traitKey = "foo1";
  private static final String user2traitVal = "xxx";

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

    user1 = featureUser("mr-user-1");
    user2 = featureUser("mr-user-2");
    user3 = featureUser("mr-user-3");

    userId1 = createUserIdentity(user1.getIdentifier(), environment.apiKey);
    userId2 = createUserIdentity(user2.getIdentifier(), environment.apiKey);
    userId3 = createUserIdentity(user3.getIdentifier(), environment.apiKey);

    switchFlagForUser(featureId, userId2, user2feature1initValue, environment.apiKey);

    assignTraitToUserIdentity(user2.getIdentifier(), user2traitKey, user2traitVal, environment.apiKey);
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

  @Test(groups = "integration")
  public void testClient_When_Stats_Disabled_Then_All_Values_Zero() {
    CacheStats cacheStats = clientCache.stats();
    assertNotNull(cacheStats);
    assertEquals(0, cacheStats.loadCount());
  }

  @Test(groups = "integration")
  public void testClient_When_Invalidate_All_Cache_Then_Cache_Empty() {
    environment.client.getUserFlagsAndTraits(user1);
    environment.client.getUserFlagsAndTraits(user2);
    assertEquals(2, clientCache.estimatedSize());

    clientCache.invalidateAll();
    assertEquals(0, clientCache.estimatedSize());
  }

  @Test(groups = "integration")
  public void testClient_When_Invalidate_Single_Cache_Entry_Then_Remaining_Cache_OK() {
    environment.client.getUserFlagsAndTraits(user1);
    environment.client.getFeatureFlags(user2);
    assertEquals(2, clientCache.estimatedSize());

    clientCache.invalidate(user1.getIdentifier());
    assertEquals(1, clientCache.estimatedSize());
    assertNull(clientCache.getIfPresent(user1.getIdentifier()));
    assertNotNull(clientCache.getIfPresent(user2.getIdentifier()));
  }

  @Test(groups = "integration")
  public void testClient_When_Cache_Max_Size_Then_Entry_Replaced() {
    // Act: cache populated with 1 entry
    environment.client.getUserFlagsAndTraits(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(clientCache.getIfPresent(user1.getIdentifier()));

    // Act: cache populated with 2 entries
    environment.client.getFeatureFlags(user2);
    assertEquals(2, clientCache.estimatedSize());
    assertNotNull(clientCache.getIfPresent(user1.getIdentifier()));
    assertNotNull(clientCache.getIfPresent(user2.getIdentifier()));

    // Act: cache remains with 2 entries
    environment.client.getUserFlagsAndTraits(user3);
    clientCache.cleanUp();
    assertEquals(2, clientCache.estimatedSize());
    assertNull(clientCache.getIfPresent(user1.getIdentifier()));
    assertNotNull(clientCache.getIfPresent(user2.getIdentifier()));
    assertNotNull(clientCache.getIfPresent(user3.getIdentifier()));
  }

  @Test(groups = "integration")
  public void testClient_When_Get_Project_Flags_Then_Not_Cached() {
    assertEquals(0, clientCache.estimatedSize());

    final List<Flag> featureFlags = environment.client.getFeatureFlags();

    assertEquals(0, clientCache.estimatedSize());
    assertNotNull(featureFlags);
    assertThat(featureFlags)
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, false),
            flag("Other Flag", null, false)
        );
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Flags_Then_Reuses_Cache_Entry() {
    final List<Flag> featureFlags1 = environment.client.getFeatureFlags(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1);

    final List<Flag> featureFlags1Again = environment.client.getFeatureFlags(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1Again);
    assertEquals(featureFlags1, featureFlags1Again);
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Flags_Then_Refetches_Only_If_Not_Present() {
    final List<Flag> featureFlags1 = environment.client.getFeatureFlags(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1);

    createFeature(new FlagsmithTestHelper.FlagFeature(
        "a-new-flag",
        null,
        environment.projectId,
        false));

    final List<Flag> featureFlags1Again = environment.client.getFeatureFlags(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(featureFlags1Again);
    assertEquals(featureFlags1, featureFlags1Again);

    clientCache.invalidate(user1.getIdentifier());

    final List<Flag> updatedFlags1 = environment.client.getFeatureFlags(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(updatedFlags1);
    assertNotEquals(featureFlags1, updatedFlags1);
    assertThat(updatedFlags1)
        .hasSize(3)
        .containsExactlyInAnyOrder(
            flag("a-new-flag", null, false),
            flag("Flag to be enabled for the user", null, false),
            flag("Other Flag", null, false)
        );
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Traits_And_Flags_Then_Reuses_Cache_Entry() {
    final FlagsAndTraits flagsAndTraits1 = environment.client.getUserFlagsAndTraits(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(flagsAndTraits1);

    createFeature(new FlagsmithTestHelper.FlagFeature(
        "a-new-flag",
        null,
        environment.projectId,
        false));

    final FlagsAndTraits flagsAndTraits1Again = environment.client.getUserFlagsAndTraits(user1);
    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(flagsAndTraits1Again);
    assertEquals(flagsAndTraits1, flagsAndTraits1Again);
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Traits_And_Flags_Then_Refetches_Only_If_Not_Present() {
    // Cache populated with 2 entries
    environment.client.getUserFlagsAndTraits(user3);
    final FlagsAndTraits flagsAndTraitsFromApi1stCall = environment.client.getUserFlagsAndTraits(user2);
    assertEquals(2, clientCache.estimatedSize());

    assertNotNull(flagsAndTraitsFromApi1stCall);
    assertThat(flagsAndTraitsFromApi1stCall.getFlags())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, true),
            flag("Other Flag", null, false)
        );
    assertThat(flagsAndTraitsFromApi1stCall.getTraits())
        .hasSize(1)
        .contains(trait(null, user2traitKey, user2traitVal));

    // Modify existing flag value and assign a new trait to user 2 in the API
    final boolean newFlagValue = !user2feature1initValue;
    switchFlagForUser(featureId, userId2, newFlagValue, environment.apiKey);
    assignTraitToUserIdentity("mr-user-2", "trait2", "new-trait-val", environment.apiKey);

    // Get flags from cache instead of reading new values from API
    final FlagsAndTraits flagsAndTraitsFromCache2ndCall = environment.client.getUserFlagsAndTraits(user2);
    assertEquals(2, clientCache.estimatedSize());
    assertEquals(flagsAndTraitsFromApi1stCall, flagsAndTraitsFromCache2ndCall);

    // Clean cache should fetch new flag value and traits
    clientCache.invalidate("mr-user-2");
    assertEquals(1, clientCache.estimatedSize());
    final FlagsAndTraits flagsAndTraitsFromApi3rdCall = environment.client.getUserFlagsAndTraits(user2);
    assertEquals(2, clientCache.estimatedSize());
    assertNotEquals(flagsAndTraitsFromApi3rdCall, flagsAndTraitsFromCache2ndCall);

    assertNotNull(flagsAndTraitsFromApi3rdCall);
    assertThat(flagsAndTraitsFromApi3rdCall.getFlags())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, newFlagValue),
            flag("Other Flag", null, false)
        );
    assertThat(flagsAndTraitsFromApi3rdCall.getTraits())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            trait(null, user2traitKey, user2traitVal),
            trait(null, "trait2", "new-trait-val")
        );
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Trait_Update_Then_Cached() {
    assertEquals(0, clientCache.estimatedSize());

    final Trait trait1 = environment.client.getTrait(user2, user2traitKey);

    assertEquals(1, clientCache.estimatedSize());
    assertNotNull(trait1);
    assertEquals(user2traitKey, trait1.getKey());
    assertEquals(user2traitVal, trait1.getValue());
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Trait_Update_Then_Not_Cached() {
    final String traitKey = user2traitKey;

    environment.client.updateTrait(user2, trait(user2.getIdentifier(), traitKey, "new value"));
    assertEquals(0, clientCache.estimatedSize());

    assertThat(environment.client.getTrait(user2, traitKey))
        .isEqualTo(trait(null, traitKey, "new value"));
    assertEquals(1, clientCache.estimatedSize());
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Trait_Update_Then_Updated_Although_Cached() {
    // Arrange
    assignTraitToUserIdentity(user2.getIdentifier(), "unchanged", "stable-value", environment.apiKey);

    assertThat(environment.client.getTrait(user2, user2traitKey))
        .isEqualTo(trait(null, user2traitKey, user2traitVal));
    assertEquals(1, clientCache.estimatedSize());

    // Act
    environment.client.updateTrait(user2, trait(user2.getIdentifier(), user2traitKey, "new value"));
    assertEquals(0, clientCache.estimatedSize());

    // Assert
    assertThat(environment.client.getTrait(user2, user2traitKey))
        .isEqualTo(trait(null, user2traitKey, "new value"));
    assertEquals(1, clientCache.estimatedSize());

    assertThat(environment.client.getTrait(user2, "unchanged"))
        .isEqualTo(trait(null, "unchanged", "stable-value"));
    assertEquals(1, clientCache.estimatedSize());
  }

  @Test(groups = "integration")
  public void testClient_When_Get_User_Trait_Update_Then_Dont_Update_When_Trait_Unchanged() {
    // Arrange
    final String traitValNew = "new-val";

    // get and cache value from API
    assertThat(environment.client.getTrait(user2, user2traitKey))
        .isEqualTo(trait(null, user2traitKey, user2traitVal));
    assertEquals(1, clientCache.estimatedSize());

    // modify value in API only, old value still cached
    assignTraitToUserIdentity(user2.getIdentifier(), user2traitKey, traitValNew, environment.apiKey);
    assignTraitToUserIdentity(user2.getIdentifier(), "unchanged", "stable-value", environment.apiKey);

    // cache remains the same
    environment.client.updateTrait(user2, trait(user2.getIdentifier(), user2traitKey, user2traitVal));
    assertEquals(1, clientCache.estimatedSize());

    // get cached old value
    assertThat(environment.client.getTrait(user2, user2traitKey))
        .isEqualTo(trait(null, user2traitKey, user2traitVal));
    assertEquals(1, clientCache.estimatedSize());
    clientCache.invalidateAll();
    assertEquals(0, clientCache.estimatedSize());

    // get new value from API
    assertThat(environment.client.getTrait(user2, user2traitKey))
        .isEqualTo(trait(null, user2traitKey, traitValNew));
    assertEquals(1, clientCache.estimatedSize());

    assertThat(environment.client.getTrait(user2, "unchanged"))
        .isEqualTo(trait(null, "unchanged", "stable-value"));
    assertEquals(1, clientCache.estimatedSize());
  }

  @Test(groups = "integration")
  public void testClient_When_Add_Traits_For_Identity_Then_Success() {
    List<Trait> traits = environment.client.identifyUserWithTraits(user2, Arrays.asList(
        trait(null, "trait_1", "some value1"),
        trait(null, "trait_2", "some value2")));
    assertEquals(1, clientCache.estimatedSize());
    assertThat(traits)
        .hasSize(3)
        .containsExactlyInAnyOrder(
            trait(null, user2traitKey, user2traitVal),
            trait(null, "trait_1", "some value1"),
            trait(null, "trait_2", "some value2")
        );

    traits = environment.client.identifyUserWithTraits(user2, Arrays.asList(
        trait(null, "trait_1", "updated value1"),
        trait(null, "trait_2", "updated value2"),
        trait(null, "trait_3", "updated value3")));
    assertEquals(1, clientCache.estimatedSize());
    assertThat(traits)
        .hasSize(4)
        .containsExactlyInAnyOrder(
            trait(null, user2traitKey, user2traitVal),
            trait(null, "trait_1", "updated value1"),
            trait(null, "trait_2", "updated value2"),
            trait(null, "trait_3", "updated value3")
        );
  }
}
