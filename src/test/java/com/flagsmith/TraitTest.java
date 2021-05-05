package com.flagsmith;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import org.testng.annotations.Test;

/**
 *
 */
public class TraitTest {

  @Test(groups = "unit")
  public void test_When_Parsed_Then_Success() throws IOException {
    final Trait trait = new Trait();
    trait.parse(getTraitJson("user-id", "trait-key", "trait-value"));

    assertEquals("trait-key", trait.getKey(), "Should have trait key");
    assertEquals("trait-value", trait.getValue(), "Should have trait value");
    assertNotNull(trait.getIdentity(), "Should have trait identity");
    assertEquals("user-id", trait.getIdentity().getIdentifier(), "Should have trait identifier");
  }

  @Test(groups = "unit")
  public void test_When_Compared_Then_Equal() throws IOException {
    final Trait trait1 = new Trait();
    trait1.parse(getTraitJson("user-id", "trait-key", "trait-value"));
    final Trait trait2 = new Trait();
    trait2.parse(getTraitJson("user-id", "trait-key", "trait-value"));
    assertTrue(trait1.equals(trait2));

    trait1.setIdentity(null);
    trait2.setIdentity(null);
    assertTrue(trait1.equals(trait2));
  }

  @Test(groups = "unit")
  public void test_When_Compared_Without_User_Then_Equal() throws IOException {
    final Trait trait1 = new Trait();
    trait1.parse(getTraitJsonWithoutUser("trait-key", "trait-value"));
    final Trait trait2 = new Trait();
    trait2.parse(getTraitJsonWithoutUser("trait-key", "trait-value"));
    assertTrue(trait1.equals(trait2));
  }

  @Test(groups = "unit")
  public void test_When_Compared_Then_Not_Equal() throws IOException {
    final Trait trait1 = new Trait();
    final Trait trait1Copy = new Trait();
    trait1.parse(getTraitJson("user-id", "trait-key", "trait-value"));
    trait1Copy.parse(getTraitJson("user-id", "trait-key", "trait-value"));

    final Trait trait2 = new Trait();
    trait2.parse(getTraitJson("user-id", "trait-key2", "trait-value"));
    assertFalse(trait1.equals(trait2));

    final Trait trait3 = new Trait();
    trait3.parse(getTraitJson("user-id3", "trait-key", "trait-value"));
    assertFalse(trait1.equals(trait3));

    final Trait trait4 = new Trait();
    trait4.parse(getTraitJson("user-id", "trait-key", "trait-value4"));
    assertFalse(trait1.equals(trait4));
  }

  private String getTraitJson(String identifier, String traitKey, String traitValue) {
    return
        "{" +
            "  \"unknown-field\": 2," +
            "  \"identity\": {" +
            "    \"identifier\": \"" + identifier + "\"" +
            "  }," +
            "  \"trait_key\": \"" + traitKey + "\"," +
            "  \"trait_value\": \"" + traitValue + "\"" +
            "}";
  }

  private String getTraitJsonWithoutUser(String traitKey, String traitValue) {
    return
        "{" +
            "  \"unknown-field\": 2," +
            "  \"trait_key\": \"" + traitKey + "\"," +
            "  \"trait_value\": \"" + traitValue + "\"" +
            "}";
  }
}
