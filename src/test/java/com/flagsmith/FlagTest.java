package com.flagsmith;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import org.testng.annotations.Test;

/**
 *
 */
public class FlagTest {

  private static final String json = "{\n" +
      "    \"id\": 2,\n" +
      "    \"feature\": {\n" +
      "      \"id\": 2,\n" +
      "      \"name\": \"font_size\",\n" +
      "      \"created_date\": \"2018-06-04T12:51:18.646762Z\",\n" +
      "      \"initial_value\": \"10\",\n" +
      "      \"description\": \"test description\",\n" +
      "      \"type\": \"CONFIG\",\n" +
      "      \"project\": 2\n" +
      "    },\n" +
      "    \"feature_state_value\": 10,\n" +
      "    \"enabled\": true,\n" +
      "    \"environment\": 2,\n" +
      "    \"identity\": null\n" +
      "  }";

  @Test(groups = "unit")
  public void test_When_Parsed_Then_Success() throws IOException {
    Flag flag = new Flag();
    flag.parse(json);

    assertNotNull(flag.getStateValue(), "Should have flag value");
    assertTrue(flag.isEnabled(), "Flag should be enabled");
    assertNotNull(flag.getFeature(), "Flag should have feature");
    assertNotNull(flag.getFeature().getName(), "Feature should have type");
    assertNotNull(flag.getFeature().getType(), "Feature should have name");
    assertNotNull(flag.getFeature().getDescription(), "Feature should have description");
  }
}
