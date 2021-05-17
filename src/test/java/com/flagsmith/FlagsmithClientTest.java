package com.flagsmith;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.flagsmith.FlagsmithTestHelper.assignTraitToUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.config;
import static com.flagsmith.FlagsmithTestHelper.createFeature;
import static com.flagsmith.FlagsmithTestHelper.createProjectEnvironment;
import static com.flagsmith.FlagsmithTestHelper.createUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.featureUser;
import static com.flagsmith.FlagsmithTestHelper.flag;
import static com.flagsmith.FlagsmithTestHelper.switchFlag;
import static com.flagsmith.FlagsmithTestHelper.switchFlagForUser;
import static com.flagsmith.FlagsmithTestHelper.trait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNull;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and
 * features ids etc as required.
 */
@Test(groups = "integration")
public class FlagsmithClientTest {

    @Test(groups = "integration")
    public void testClient_When_Get_Features_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_Features_Then_Success",
                "TEST");

        createFeature(new FlagsmithTestHelper.FlagFeature(
                "Flag 1",
                "Description for Flag 1",
                environment.projectId,
                true));
        createFeature(new FlagsmithTestHelper.FlagFeature(
                "Flag 2",
                "Description for Flag 2",
                environment.projectId,
                false));
        createFeature(new FlagsmithTestHelper.ConfigFeature(
                "Config 1",
                "Description for Config 1",
                environment.projectId,
                "xxx"));
        createFeature(new FlagsmithTestHelper.ConfigFeature(
                "Config 2",
                "Description for Config 2",
                environment.projectId,
                "foo"));

        final List<Flag> featureFlags = environment.client.getFeatureFlags();

        assertThat(featureFlags)
                .isNotNull()
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        flag("Flag 1", "Description for Flag 1", true),
                        flag("Flag 2", "Description for Flag 2", false),
                        config("Config 1", "Description for Config 1", "xxx"),
                        config("Config 2", "Description for Config 2", "foo")
                );
    }

    @Test(groups = "integration")
    public void testClient_When_Has_Feature_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Has_Feature_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagsmithTestHelper.FlagFeature(
                "Flag disabled",
                null,
                environment.projectId,
                false));

        switchFlag(featureId, true, environment.apiKey);

        final boolean enabled = environment.client.hasFeatureFlag("Flag disabled");
        assertThat(enabled)
                .describedAs("Disabled by default, but enabled")
                .isTrue();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_Features_For_User_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_Features_For_User_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagsmithTestHelper.FlagFeature(
                "Flag to be enabled for the user",
                null,
                environment.projectId,
                false));
        createFeature(new FlagsmithTestHelper.FlagFeature(
                "Other Flag",
                null,
                environment.projectId,
                false));

        createUserIdentity("first-user", environment.apiKey);
        final int secondUserId = createUserIdentity("second-user", environment.apiKey);

        switchFlagForUser(featureId, secondUserId, true, environment.apiKey);

        final FeatureUser user = featureUser("second-user");

        final List<Flag> listForUserEnabled = environment.client.getFeatureFlags(user);
        assertThat(listForUserEnabled)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        flag("Flag to be enabled for the user", null, true),
                        flag("Other Flag", null, false)
                );

        final List<Flag> listWithoutUser = environment.client.getFeatureFlags();
        assertThat(listWithoutUser)
                .hasSize(2)
                .allSatisfy(flag -> assertThat(flag.isEnabled()).isFalse());

        switchFlagForUser(featureId, secondUserId, false, environment.apiKey);

        final List<Flag> listForUserDisabled = environment.client.getFeatureFlags(user);
        assertThat(listForUserDisabled)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        flag("Flag to be enabled for the user", null, false),
                        flag("Other Flag", null, false)
                );
    }


    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_Then_Success",
                "TEST");

        assignTraitToUserIdentity("user-with-traits", "foo1", "bar", environment.apiKey);
        assignTraitToUserIdentity("user-with-traits", "foo2", 123, environment.apiKey);
        assignTraitToUserIdentity("user-with-traits", "foo3", 3.14, environment.apiKey);
        assignTraitToUserIdentity("user-with-traits", "foo4", false, environment.apiKey);

        final FeatureUser user = featureUser("user-with-traits");

        final List<Trait> traits = environment.client.getTraits(user);
        assertThat(traits)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        trait(null, "foo1", "bar"),
                        trait(null, "foo2", "123"),
                        trait(null, "foo3", "3.14"),
                        trait(null, "foo4", "false")
                );
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Keys_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_For_Keys_Then_Success",
                "TEST");

        assignTraitToUserIdentity("user-with-key-traits", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("user-with-key-traits", "foo2", "yyy", environment.apiKey);
        assignTraitToUserIdentity("user-with-key-traits", "foo3", "zzz", environment.apiKey);

        final FeatureUser user = featureUser("user-with-key-traits");

        final List<Trait> traits = environment.client.getTraits(user, "foo2", "foo3", "foo-missing");
        assertThat(traits)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        trait(null, "foo2", "yyy"),
                        trait(null, "foo3", "zzz")
                );
        assertThat(traits)
                .extracting(Trait::getKey)
                .doesNotContain("foo-missing");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Empty() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_For_Invalid_User_Then_Return_Empty",
                "TEST");

        assignTraitToUserIdentity("mr-user", "foo", "bar", environment.apiKey);

        final FeatureUser user = featureUser("invalid-user");

        final List<Trait> traits = environment.client.getTraits(user);
        assertThat(traits)
                .isEmpty();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagsmithTestHelper.FlagFeature(
                "Flag to be enabled for the user",
                null,
                environment.projectId,
                false));
        createFeature(new FlagsmithTestHelper.FlagFeature(
                "Other Flag",
                null,
                environment.projectId,
                false));

        createUserIdentity("mr-user-1", environment.apiKey);
        final int userId = createUserIdentity("mr-user-2", environment.apiKey);

        switchFlagForUser(featureId, userId, true, environment.apiKey);

        assignTraitToUserIdentity("mr-user-2", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("mr-user-999", "foo2", "yyy", environment.apiKey);

        final FeatureUser user = featureUser("mr-user-2");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        assertThat(flagsAndTraits)
                .isNotNull();
        assertThat(flagsAndTraits.getFlags())
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        flag("Flag to be enabled for the user", null, true),
                        flag("Other Flag", null, false)
                );
        assertThat(flagsAndTraits.getTraits())
                .hasSize(1)
                .contains(trait(null, "foo1", "xxx"));
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Empty() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_And_Flags_For_Invalid_User_Then_Return_Empty",
                "TEST");

        createFeature(new FlagsmithTestHelper.FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));

        assignTraitToUserIdentity("mr-user", "foo", "bar", environment.apiKey);

        final FeatureUser user = featureUser("different-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        assertThat(flagsAndTraits)
                .isNotNull();
        assertThat(flagsAndTraits.getFlags())
                .hasSize(1)
                .contains(flag("The Flag", null, false));
        assertThat(flagsAndTraits.getTraits())
                .isEmpty();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Trait_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        createFeature(new FlagsmithTestHelper.FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo2", "yyy", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo3", "zzz", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final Trait trait = FlagsmithClient.getTrait(flagsAndTraits, "foo2");
        assertThat(trait)
                .isNotNull()
                .isEqualTo(trait(null, "foo2", "yyy"));
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Traits_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        createFeature(new FlagsmithTestHelper.FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo2", "yyy", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo3", "zzz", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final List<Trait> traits = FlagsmithClient.getTraits(flagsAndTraits, "foo2", "foo3", "foo-missing");
        assertThat(traits)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        trait(null, "foo2", "yyy"),
                        trait(null, "foo3", "zzz")
                );
        assertThat(traits)
                .extracting(Trait::getKey)
                .doesNotContain("foo-missing");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_FLag_Value_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        createFeature(new FlagsmithTestHelper.FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));
        createFeature(new FlagsmithTestHelper.ConfigFeature(
                "font_size",
                null,
                environment.projectId,
                "11pt"));

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final String fontSize = environment.client.getFeatureFlagValue("font_size", flagsAndTraits);
        assertThat(fontSize)
                .isEqualTo("11pt");
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_FLag_Enabled_From_Traits_And_Flags_For_Keys_Then_Success",
                "TEST");

        final int featureId = createFeature(new FlagsmithTestHelper.FlagFeature(
                "The Flag",
                null,
                environment.projectId,
                false));
        final int userId = createUserIdentity("mr-user", environment.apiKey);
        switchFlagForUser(featureId, userId, true, environment.apiKey);

        assignTraitToUserIdentity("mr-user", "foo1", "xxx", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        final FlagsAndTraits flagsAndTraits = environment.client.getUserFlagsAndTraits(user);
        final boolean enabled = environment.client.hasFeatureFlag("The Flag", flagsAndTraits);
        assertThat(enabled)
                .isTrue();
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Trait_Then_Success",
                "TEST");

        assignTraitToUserIdentity("mr-user", "cookie", "value", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");
        final Trait trait = environment.client.getTrait(user, "cookie");
        assertThat(trait)
                .isEqualTo(trait(null, "cookie", "value"));
    }

    @Test(groups = "integration")
    public void testClient_When_Get_User_Trait_Update_Then_Updated() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Get_User_Trait_Update_Then_Updated",
                "TEST");

        assignTraitToUserIdentity("mr-user", "cookie", "old value", environment.apiKey);
        assignTraitToUserIdentity("mr-user", "foo", "bar", environment.apiKey);

        final FeatureUser user = featureUser("mr-user");

        assertThat(environment.client.getTrait(user, "cookie"))
                .isEqualTo(trait(null, "cookie", "old value"));

        environment.client.updateTrait(user, trait("mr-user", "cookie", "new value"));

        assertThat(environment.client.getTrait(user, "cookie"))
                .isEqualTo(trait(null, "cookie", "new value"));
        assertThat(environment.client.getTrait(user, "foo"))
                .isEqualTo(trait(null, "foo", "bar"));
    }

    @Test(groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed",
                "TEST");

        assertThatThrownBy(() ->
                environment.client.identifyUserWithTraits(null,
                        Collections.singletonList(trait(null, "x", "y"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user is marked non-null but is null");
    }

    @Test(groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_With_Missing_User_Identifier_Then_Failed() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
            "testClient_When_Add_Traits_For_Identity_With_Missing_Identity_Then_Failed",
            "TEST");

        assertThatThrownBy(() ->
            environment.client.identifyUserWithTraits(new FeatureUser(),
                Collections.singletonList(trait(null, "x", "y"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing user identifier");
    }

    @Test(groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
                "testClient_When_Add_Traits_For_Identity_Then_Success",
                "TEST");

        final FeatureUser user = featureUser("i-am-user-with-traits");

        final List<Trait> traits = environment.client.identifyUserWithTraits(user, Arrays.asList(
                trait(null, "trait_1", "some value1"),
                trait(null, "trait_2", "some value2"))).getTraits();

        assertThat(traits)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        trait(null, "trait_1", "some value1"),
                        trait(null, "trait_2", "some value2")
                );
    }

    @Test(groups = "integration")
    public void testClient_When_Add_Traits_For_Identity_To_Existing_Identity_Then_Success() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
            "testClient_When_Add_Traits_For_Identity_Then_Success",
            "TEST");

        final FeatureUser user = featureUser("i-am-user-with-traits");

        List<Trait> traits = environment.client.identifyUserWithTraits(user, Arrays.asList(
            trait(null, "trait_1", "some value1"),
            trait(null, "trait_2", "some value2"),
            trait(null, "trait_3", "some value3"))).getTraits();

        assertThat(traits)
            .hasSize(3)
            .containsExactlyInAnyOrder(
                trait(null, "trait_1", "some value1"),
                trait(null, "trait_2", "some value2"),
                trait(null, "trait_3", "some value3")
            );

        // Update existing identity (trait 2 is missing on purpose)
        traits = environment.client.identifyUserWithTraits(user, Arrays.asList(
            trait(null, "extra_trait", "extra value"),
            trait(null, "trait_1", "updated value1"),
            trait(null, "trait_3", "some value3"))).getTraits();

        assertThat(traits)
            .hasSize(4)
            .containsExactlyInAnyOrder(
                trait(null, "extra_trait", "extra value"),
                trait(null, "trait_1", "updated value1"),
                trait(null, "trait_2", "some value2"),
                trait(null, "trait_3", "some value3")
            );
    }

    @Test(groups = "integration")
    public void testClient_When_Cache_Disabled_Return_Null() {
        final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
            "testClient_When_Cache_Disabled_Return_Null",
            "TEST");

        FlagsmithCache cache = environment.client.getCache();

        assertNull(cache);
    }
}
