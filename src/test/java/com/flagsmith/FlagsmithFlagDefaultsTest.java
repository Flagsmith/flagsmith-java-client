package com.flagsmith;

import static org.assertj.core.api.Assertions.assertThat;

import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

public class FlagsmithFlagDefaultsTest {

  private FlagsmithFlagDefaults sut;

  @BeforeEach
  public void init() {
    sut = new FlagsmithFlagDefaults();
  }

  private DefaultFlag defaultFlagHandler(String name) {
    if (name.equals("flaggy")) {
      DefaultFlag flag = new DefaultFlag();
      flag.setEnabled(Boolean.TRUE);
      flag.setFeatureName(name);
      return flag;
    }

    return new DefaultFlag();
  }

  @Test
  public void getDefaultFlags_withCustomDefaultValues() throws FlagsmithClientError {
    // Arrange
    sut.setDefaultFlagValueFunc((name) -> defaultFlagHandler(name));

    // Act
    final Flags flags = Flags.fromApiFlags(new ArrayList<>(), null, sut);

    // Assert
    assertThat(flags.getFlag("new-one").getFeatureName()).isEqualTo(null);
    assertThat(flags.getFlag("new-one").getEnabled()).isEqualTo(null);
    assertThat(flags.getFlag("flaggy").getFeatureName()).isEqualTo("flaggy");
    assertThat(flags.getFlag("flaggy").getEnabled()).isEqualTo(Boolean.TRUE);
  }
}
