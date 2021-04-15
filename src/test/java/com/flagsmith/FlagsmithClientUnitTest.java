package com.flagsmith;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class FlagsmithClientUnitTest {

  private FlagsmithClient.Builder builder;

  @BeforeMethod(groups = "integration-offline")
  public void init() {
    builder = FlagsmithClient.newBuilder()
        .setApiKey("API_KEY")
        .withApiUrl("http://bad-url");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nullInput_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    boolean enabled = flagsmithClient.hasFeatureFlag("hero", flagsAndTraits);

    // Assert
    assertFalse(enabled, "Should not have feature enabled");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nullInput_returnTrue() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagPredicate((String flagName) -> {
          assertEquals("hero", flagName);
          return true;
        })
        .build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    boolean enabled = flagsmithClient.hasFeatureFlag("hero", flagsAndTraits);

    // Assert
    assertTrue(enabled, "Should have feature enabled");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nameOnly_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();

    // Act
    boolean enabled = flagsmithClient.hasFeatureFlag("hero");

    // Assert
    assertFalse(enabled, "Should not have feature enabled");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nameOnly_returnTrue() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagPredicate((String flagName) -> {
          assertEquals("hero", flagName);
          return true;
        }).build();

    // Act
    boolean enabled = flagsmithClient.hasFeatureFlag("hero");

    // Assert
    assertTrue(enabled, "Should have feature enabled");
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nullInput_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    String value = flagsmithClient.getFeatureFlagValue("hero", flagsAndTraits);

    // Assert
    assertNull(value);
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nullInput_returnTrue() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagValueFunction((String flagName) -> {
          assertEquals("hero", flagName);
          return "value";
        })
        .build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    String value = flagsmithClient.getFeatureFlagValue("hero", flagsAndTraits);

    // Assert
    assertEquals("value", value);
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nameOnly_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();

    // Act
    String value = flagsmithClient.getFeatureFlagValue("hero");

    // Assert
    assertNull(value);
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nameOnly_returnTrue() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagValueFunction((String flagName) -> {
          assertEquals("hero", flagName);
          return "value";
        }).build();

    // Act
    String value = flagsmithClient.getFeatureFlagValue("hero");

    // Assert
    assertEquals("value", value);
  }
}
