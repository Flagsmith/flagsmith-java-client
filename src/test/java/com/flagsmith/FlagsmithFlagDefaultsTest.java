package com.flagsmith;

import static com.flagsmith.FlagsmithTestHelper.typelessFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

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

  @Test(groups = "unit")
  public void constructorDefaults() {
    // Assert
    assertTrue(sut.getDefaultFlags().isEmpty());
    assertTrue(sut.getDefaultFeatureFlagNames().isEmpty());
  }

  @Test(groups = "unit")
  public void getDefaultFeatureFlagNames_inLowerCase() {
    // Arrange
    sut.setDefaultFeatureFlags(new HashSet<String>() {{
      add("flagname");
      add("FLAGNAME");
      add("FLAGs-MIXED1");
    }});

    // Act
    final Set<String> defaultFeatureFlagNames = sut.getDefaultFeatureFlagNames();

    // Assert
    assertThat(defaultFeatureFlagNames).hasSize(2)
        .containsExactlyInAnyOrder("flagname", "flags-mixed1");
  }

  @Test(groups = "unit")
  public void getDefaultFlags_withDefaultValues() {
    // Arrange
    sut.setDefaultFeatureFlags(new HashSet<String>() {{
      add("flag-name-1");
      add("flag-name-2");
      add("flag-name-3");
    }});

    // Act
    final List<Flag> defaultFlags = sut.getDefaultFlags();

    // Assert
    assertThat(defaultFlags).hasSize(3).containsExactlyInAnyOrder(
        typelessFlag("flag-name-1", false, null),
        typelessFlag("flag-name-2", false, null),
        typelessFlag("flag-name-3", false, null)
    );
  }

  @Test(groups = "unit")
  public void getDefaultFlags_withCustomDefaultValues() {
    // Arrange
    sut.setDefaultFlagPredicate((String flagName) -> true);
    sut.setDefaultFlagValueFunc((String flagName) -> "myDefaultValue-" + flagName);
    sut.setDefaultFeatureFlags(new HashSet<String>() {{
      add("flag-name-1");
      add("flag-name-2");
      add("flag-name-3");
    }});

    // Act
    final List<Flag> defaultFlags = sut.getDefaultFlags();

    // Assert
    assertThat(defaultFlags).hasSize(3).containsExactlyInAnyOrder(
        typelessFlag("flag-name-1", true, "myDefaultValue-flag-name-1"),
        typelessFlag("flag-name-2", true, "myDefaultValue-flag-name-2"),
        typelessFlag("flag-name-3", true, "myDefaultValue-flag-name-3")
    );
  }

  @Test(groups = "unit")
  public void enrichWithDefaultFlags_addDefaultFlagsThatWereNotFetched() {
    // Arrange
    final FlagsAndTraits flagsFetchedFromFlagsmith = new FlagsAndTraits();
    final ArrayList<Flag> existingFlags = new ArrayList<Flag>() {{
      add(typelessFlag("fetched-flag-1", true, "fetched-value-1"));
      add(typelessFlag("fetched-flag-2", true, "fetched-value-2"));
    }};
    flagsFetchedFromFlagsmith.setFlags(existingFlags);
    flagsFetchedFromFlagsmith.setTraits(new ArrayList<Trait>() {{
      add(new Trait());
    }});

    sut.setDefaultFlagValueFunc((String flagName) -> "myDefaultValue-" + flagName);

    sut.setDefaultFeatureFlags(new HashSet<String>() {{
      add("flag-name-1");
      add("flag-name-2");
      add("flag-name-3");
    }});

    // Act
    final FlagsAndTraits enrichedFlagsAndTraits = sut
        .enrichWithDefaultFlags(flagsFetchedFromFlagsmith);

    // Assert
    assertThat(enrichedFlagsAndTraits.getFlags()).hasSize(5).containsExactlyInAnyOrder(
        typelessFlag("fetched-flag-1", true, "fetched-value-1"),
        typelessFlag("fetched-flag-2", true, "fetched-value-2"),
        typelessFlag("flag-name-1", false, "myDefaultValue-flag-name-1"),
        typelessFlag("flag-name-2", false, "myDefaultValue-flag-name-2"),
        typelessFlag("flag-name-3", false, "myDefaultValue-flag-name-3")
    );
    assertThat(enrichedFlagsAndTraits.getTraits()).hasSize(1);
  }

  @Test(groups = "unit")
  public void enrichWithDefaultFlags_addDefaultFlagsWhenNothingWasFetched() {
    // Arrange
    final FlagsAndTraits flagsFetchedFromFlagsmith = new FlagsAndTraits();
    flagsFetchedFromFlagsmith.setFlags(new ArrayList<>());

    sut.setDefaultFlagValueFunc((String flagName) -> "myDefaultValue-" + flagName);

    sut.setDefaultFeatureFlags(new HashSet<String>() {{
      add("flag-name-1");
      add("flag-name-2");
      add("flag-name-3");
    }});

    // Act
    final FlagsAndTraits enrichedFlagsAndTraits = sut
        .enrichWithDefaultFlags(flagsFetchedFromFlagsmith);

    // Assert
    assertThat(enrichedFlagsAndTraits.getFlags()).hasSize(3).containsExactlyInAnyOrder(
        typelessFlag("flag-name-1", false, "myDefaultValue-flag-name-1"),
        typelessFlag("flag-name-2", false, "myDefaultValue-flag-name-2"),
        typelessFlag("flag-name-3", false, "myDefaultValue-flag-name-3")
    );
    assertNull(enrichedFlagsAndTraits.getTraits());
  }

  @Test(groups = "unit")
  public void enrichWithDefaultFlags_addDefaultFlagsWhenFlagsAreNull() {
    // Arrange
    final FlagsAndTraits flagsFetchedFromFlagsmith = new FlagsAndTraits();
    flagsFetchedFromFlagsmith.setFlags(null);
    flagsFetchedFromFlagsmith.setTraits(null);

    sut.setDefaultFlagValueFunc((String flagName) -> "myDefaultValue-" + flagName);

    sut.setDefaultFeatureFlags(new HashSet<String>() {{
      add("flag-name-1");
      add("flag-name-2");
      add("flag-name-3");
    }});

    // Act
    final FlagsAndTraits enrichedFlagsAndTraits = sut
        .enrichWithDefaultFlags(flagsFetchedFromFlagsmith);

    // Assert
    assertThat(enrichedFlagsAndTraits.getFlags()).hasSize(3).containsExactlyInAnyOrder(
        typelessFlag("flag-name-1", false, "myDefaultValue-flag-name-1"),
        typelessFlag("flag-name-2", false, "myDefaultValue-flag-name-2"),
        typelessFlag("flag-name-3", false, "myDefaultValue-flag-name-3")
    );
    assertNull(enrichedFlagsAndTraits.getTraits());
  }
}