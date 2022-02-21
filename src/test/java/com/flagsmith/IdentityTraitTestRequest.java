package com.flagsmith;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import org.testng.annotations.Test;

public class IdentityTraitTest {

  private static final String json = "{\n" +
      "  \"identifier\": \"test \",\n" +
      "  \"traits\": [\n" +
      "    {\"my_trait\": 123},\n" +
      "    {\"my_other_trait\": \"string value\"}\n" +
      "  \n" +
      "]}";

  @Test(groups = "unit")
  public void test_When_Parsed_Then_Success() throws IOException {
    final IdentityTraits trait = new IdentityTraits();
    trait.parse(json);

    assertNotNull(trait.getIdentifier(), "Should have identifier");
    assertTrue(trait.getTraits().size() == 2, "Should have 2 traits");
  }
}
