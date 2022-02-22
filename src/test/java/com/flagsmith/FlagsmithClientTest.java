package com.flagsmith;

import static com.flagsmith.FlagsmithTestHelper.assignTraitToUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.config;
import static com.flagsmith.FlagsmithTestHelper.createFeature;
import static com.flagsmith.FlagsmithTestHelper.createProjectEnvironment;
import static com.flagsmith.FlagsmithTestHelper.createUserIdentity;
import static com.flagsmith.FlagsmithTestHelper.featureUser;
import static com.flagsmith.FlagsmithTestHelper.flag;
import static com.flagsmith.FlagsmithTestHelper.switchFlag;
import static com.flagsmith.FlagsmithTestHelper.switchFlagForUser;
//import static com.flagsmith.FlagsmithTestHelper.trait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.flagsmith.FlagsmithTestHelper.FlagFeature;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.interfaces.FlagsmithCache;
import com.flagsmith.models.Flags;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Unit tests are env specific and will probably will need to adjust keys, identities and features
 * ids etc as required.
 */
@Test(groups = "integration")
public class FlagsmithClientTest {

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_Features_Then_Success() throws FlagsmithApiError {
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

    final Flags featureFlags = environment.client.getEnvironmentFlags();

    assertThat(featureFlags.getFlags().values())
        .isNotNull()
        .hasSize(4)
        .containsExactlyInAnyOrder(
            flag("Flag 1", "Description for Flag 1", true),
            flag("Flag 2", "Description for Flag 2", false),
            config("Config 1", "Description for Config 1", "xxx"),
            config("Config 2", "Description for Config 2", "foo")
        );
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Get_Features_For_User_Then_Success() throws FlagsmithApiError {
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

    final String user = "second-user";

    final Flags listForUserEnabled = environment.client.getEnvironmentFlags();
    assertThat(listForUserEnabled.getFlags().values())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, true),
            flag("Other Flag", null, false)
        );

    final Flags listWithoutUser = environment.client.getEnvironmentFlags();
    assertThat(listWithoutUser.getFlags().values())
        .hasSize(2)
        .allSatisfy(flag -> assertThat(flag.getEnabled()).isFalse());

    switchFlagForUser(featureId, secondUserId, false, environment.apiKey);

    final Flags listForUserDisabled = environment.client.getEnvironmentFlags();
    assertThat(listForUserDisabled.getFlags().values())
        .hasSize(2)
        .containsExactlyInAnyOrder(
            flag("Flag to be enabled for the user", null, false),
            flag("Other Flag", null, false)
        );
  }

  @Test(groups = "integration", enabled = false)
  public void testClient_When_Cache_Disabled_Return_Null() {
    final FlagsmithTestHelper.ProjectEnvironment environment = createProjectEnvironment(
        "testClient_When_Cache_Disabled_Return_Null",
        "TEST");

    FlagsmithCache cache = environment.client.getCache();

    assertNull(cache);
  }
}
