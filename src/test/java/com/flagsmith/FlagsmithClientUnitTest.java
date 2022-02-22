package com.flagsmith;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FlagsmithClientUnitTest {

  private FlagsmithClient.Builder builder;

  @BeforeMethod(groups = "unit")
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
    final boolean enabled = flagsmithClient.hasFeatureFlag("hero", flagsAndTraits);

    // Assert
    assertFalse(enabled, "Should not have feature enabled");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nullInput_returnTrue() {
    // Arrange
    final String featureId = "hero";
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagPredicate((String flagName) -> {
          assertEquals(featureId, flagName);
          return true;
        })
        .build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    final boolean enabled = flagsmithClient.hasFeatureFlag(featureId, flagsAndTraits);

    // Assert
    assertTrue(enabled, "Should have feature enabled");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nameOnly_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();

    // Act
    final boolean enabled = flagsmithClient.hasFeatureFlag("hero");

    // Assert
    assertFalse(enabled, "Should not have feature enabled");
  }

  @Test(groups = "unit")
  public void hasFeatureFlag_nameOnly_returnTrue() {
    // Arrange
    final String featureId = "hero";
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagPredicate((String flagName) -> {
          assertEquals(featureId, flagName);
          return true;
        }).build();

    // Act
    final boolean enabled = flagsmithClient.hasFeatureFlag(featureId);

    // Assert
    assertTrue(enabled, "Should have feature enabled");
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nullInput_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    final String value = flagsmithClient.getFeatureFlagValue("hero", flagsAndTraits);

    // Assert
    assertNull(value);
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nullInput_returnTrue() {
    // Arrange
    final String expectedValue = "value";
    final String featureId = "hero";
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagValueFunction((String flagName) -> {
          assertEquals(featureId, flagName);
          return expectedValue;
        })
        .build();
    final FlagsAndTraits flagsAndTraits = null;

    // Act
    final String value = flagsmithClient.getFeatureFlagValue(featureId, flagsAndTraits);

    // Assert
    assertEquals(expectedValue, value);
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nameOnly_returnFalse() {
    // Arrange
    final FlagsmithClient flagsmithClient = builder.build();

    // Act
    final String value = flagsmithClient.getFeatureFlagValue("hero");

    // Assert
    assertNull(value);
  }

  @Test(groups = "unit")
  public void getFeatureFlagValue_nameOnly_returnTrue() {
    // Arrange
    final String expectedValue = "value";
    final String featureId = "hero";
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagValueFunction((String flagName) -> {
          assertEquals(featureId, flagName);
          return expectedValue;
        }).build();

    // Act
    final String value = flagsmithClient.getFeatureFlagValue(featureId);

    // Assert
    assertEquals(expectedValue, value);
  }
/*
  @Test(groups = "unit")
  public void getFeatureFlagValue_defaultFlag() {
    // Arrange
    final String expectedValue = "value";
    final String featureId = "default-flag-added";
    final FlagsmithClient flagsmithClient = builder
        .setDefaultFlagValueFunction((String flagName) -> expectedValue)
        .setDefaultFeatureFlags(new HashSet<String>() {{
          add(featureId);
        }})
        .build();

    // Act
    final String value = flagsmithClient.getFeatureFlagValue(featureId);

    // Assert
    assertEquals(expectedValue, value);
  }*/
}
