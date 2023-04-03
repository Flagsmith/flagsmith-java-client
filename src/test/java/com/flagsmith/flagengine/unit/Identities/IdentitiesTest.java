package com.flagsmith.flagengine.unit.Identities;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.identities.IdentityModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdentitiesTest {

  @Test
  public void testBuildIdentityModelFromDictionaryNoFeatureStates() throws Exception {
    String json = "{\n" +
        "  \"id\": 1,\n" +
        "  \"identifier\": \"test-identity\",\n" +
        "  \"environment_api_key\": \"api-key\",\n" +
        "  \"created_date\": \"2021-08-22T06:25:23.406995Z\",\n" +
        "  \"identity_traits\": [{\"trait_key\": \"trait_key\", \"trait_value\": \"trait_value\"}]\n" +
        "}";

    JsonNode node = MapperFactory.getMapper().readTree(json);
    IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

    Assertions.assertNotNull(identityModel.getIdentityFeatures());
    Assertions.assertEquals(identityModel.getIdentityFeatures().size(), 0);

    Assertions.assertNotNull(identityModel.getIdentityTraits());
    Assertions.assertEquals(identityModel.getIdentityTraits().size(), 1);
  }

  @Test
  public void testBuildIdentityModelFromDictionaryUsesIdentityFeatureListForIdentityFeatures()
      throws Exception {
    String json = "{\n" +
        "        \"id\": 1,\n" +
        "        \"identifier\": \"test-identity\",\n" +
        "        \"environment_api_key\": \"api-key\",\n" +
        "        \"created_date\": \"2021-08-22T06:25:23.406995Z\",\n" +
        "        \"identity_features\": [\n" +
        "            {\n" +
        "                \"id\": 1,\n" +
        "                \"feature\": {\n" +
        "                    \"id\": 1,\n" +
        "                    \"name\": \"test_feature\",\n" +
        "                    \"type\": \"STANDARD\"\n" +
        "                },\n" +
        "                \"enabled\": true,\n" +
        "                \"feature_state_value\": \"some-value\"\n" +
        "            }\n" +
        "        ]\n" +
        "    }";

    JsonNode node = MapperFactory.getMapper().readTree(json);
    IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

    Assertions.assertNotNull(identityModel.getIdentityFeatures());
    Assertions.assertEquals(identityModel.getIdentityFeatures().size(), 1);
  }

  @Test
  public void testBuildBuildIdentityModelFromDictCreatesIdentityUuid() throws Exception {
    String json = "{\"identifier\": \"test_user\", \"environment_api_key\": \"some_key\"}";

    JsonNode node = MapperFactory.getMapper().readTree(json);
    IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

    Assertions.assertNotNull(identityModel);
    Assertions.assertNotNull(identityModel.getIdentityUuid());
  }

  @Test
  public void testBuildIdentityModelFromDictionaryWithFeatureStates() throws Exception {
    String json = "{\n" +
        "        \"id\": 1,\n" +
        "        \"identifier\": \"test-identity\",\n" +
        "        \"environment_api_key\": \"api-key\",\n" +
        "        \"created_date\": \"2021-08-22T06:25:23.406995Z\",\n" +
        "        \"identity_features\": [\n" +
        "            {\n" +
        "                \"id\": 1,\n" +
        "                \"feature\": {\n" +
        "                    \"id\": 1,\n" +
        "                    \"name\": \"test_feature\",\n" +
        "                    \"type\": \"STANDARD\"\n" +
        "                },\n" +
        "                \"enabled\": true,\n" +
        "                \"feature_state_value\": \"some-value\"\n" +
        "            }\n" +
        "        ]\n" +
        "    }";

    JsonNode node = MapperFactory.getMapper().readTree(json);
    IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

    Assertions.assertNotNull(identityModel);
    Assertions.assertNotNull(identityModel.getIdentityFeatures());
    Assertions.assertEquals(identityModel.getIdentityFeatures().size(), 1);
  }
}
