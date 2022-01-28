package com.flagsmith.flagengine.unit.Identities;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.utils.encode.JSONEncoder;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class IdentitiesTest {

    public void testBuildIdentityModelFromDictionaryNoFeatureStates() throws Exception {
        String json = "{\n" +
                "        \"id\": 1,\n" +
                "        \"identifier\": \"test-identity\",\n" +
                "        \"environment_api_key\": \"api-key\",\n" +
                "        \"created_date\": \"2021-08-22T06:25:23.406995Z\",\n" +
                "        \"identity_traits\": [{\"trait_key\": \"trait_key\", \"trait_value\": \"trait_value\"}]\n" +
                "    }";

        JsonNode node = JSONEncoder.getMapper().readTree(json);
        IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

        Assert.assertNotNull(identityModel.getIdentityFeatures());
        Assert.assertEquals(identityModel.getIdentityFeatures().size(), 0);

        Assert.assertNotNull(identityModel.getIdentityTraits());
        Assert.assertEquals(identityModel.getIdentityTraits().size(), 1);
    }

    public void testBuildIdentityModelFromDictionaryUsesIdentityFeatureListForIdentityFeatures() throws Exception {
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

        JsonNode node = JSONEncoder.getMapper().readTree(json);
        IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

        Assert.assertNotNull(identityModel.getIdentityFeatures());
        Assert.assertEquals(identityModel.getIdentityFeatures().size(), 1);
    }

    public void testBuildBuildIdentityModelFromDictCreatesIdentityUuid() throws Exception {
        String json = "{\"identifier\": \"test_user\", \"environment_api_key\": \"some_key\"}";

        JsonNode node = JSONEncoder.getMapper().readTree(json);
        IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

        Assert.assertNotNull(identityModel);
        Assert.assertNotNull(identityModel.getIdentityUuid());
    }

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

        JsonNode node = JSONEncoder.getMapper().readTree(json);
        IdentityModel identityModel = IdentityModel.load(node, IdentityModel.class);

        Assert.assertNotNull(identityModel);
        Assert.assertNotNull(identityModel.getIdentityFeatures());
        Assert.assertEquals(identityModel.getIdentityFeatures().size(), 1);
    }
}
