package com.flagsmith;

import static com.flagsmith.FlagsmithTestHelper.flag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flag;
import com.flagsmith.models.Flags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FlagsmithFlagDefaultsTest {

  private FlagsmithFlagDefaults sut;

  @BeforeMethod(groups = "unit")
  public void init() {
    sut = new FlagsmithFlagDefaults();
  }

  private BaseFlag createDummyFlag(String name) {
    if (name.equals("flaggy")) {
      Flag flag = new Flag();
      flag.setEnabled(Boolean.TRUE);
      flag.setFeatureName(name);
      return flag;
    }

    return new Flag();
  }

  @Test(groups = "unit")
  public void getDefaultFlags_withCustomDefaultValues() throws FlagsmithClientError {
    // Arrange
    sut.setDefaultFlagValueFunc((String flagName) -> createDummyFlag(flagName));

    // Act
    final Flags flags = Flags.fromApiFlags(new ArrayList<>(), null, sut);

    // Assert
    assertThat(flags.getFlag("new-one").getFeatureName()).isEqualTo(null);
    assertThat(flags.getFlag("new-one").getEnabled()).isEqualTo(null);
    assertThat(flags.getFlag("flaggy").getFeatureName()).isEqualTo("flaggy");
    assertThat(flags.getFlag("flaggy").getEnabled()).isEqualTo(Boolean.TRUE);
  }
}