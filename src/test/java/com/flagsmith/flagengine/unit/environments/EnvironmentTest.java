package com.flagsmith.flagengine.unit.environments;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.helpers.FeatureStateHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnvironmentTest {

  @Test
  public void test_get_flags_for_environment_returns_feature_states_for_environment_dictionary()
      throws Exception {
    String json = "{\n" +
        "        \"id\": 1,\n" +
        "        \"api_key\": \"api-key\",\n" +
        "        \"project\": {\n" +
        "            \"id\": 1,\n" +
        "            \"name\": \"test project\",\n" +
        "            \"organisation\": {\n" +
        "                \"id\": 1,\n" +
        "                \"name\": \"Test Org\",\n" +
        "                \"stop_serving_flags\": false,\n" +
        "                \"persist_trait_data\": true,\n" +
        "                \"feature_analytics\": true\n" +
        "            },\n" +
        "            \"hide_disabled_flags\": false\n" +
        "        },\n" +
        "        \"feature_states\": [\n" +
        "            {\n" +
        "                \"id\": 1,\n" +
        "                \"enabled\": true,\n" +
        "                \"feature_state_value\": null,\n" +
        "                \"feature\": {\"id\": 1, \"name\": \"enabled_feature\", \"type\": \"STANDARD\"}\n" +
        "            },\n" +
        "            {\n" +
        "                \"id\": 2,\n" +
        "                \"enabled\": false,\n" +
        "                \"feature_state_value\": null,\n" +
        "                \"feature\": {\"id\": 2, \"name\": \"disabled_feature\", \"type\": \"STANDARD\"}\n" +
        "            },\n" +
        "            {\n" +
        "                \"id\": 3,\n" +
        "                \"enabled\": true,\n" +
        "                \"feature_state_value\": \"foo\",\n" +
        "                \"feature\": {\n" +
        "                    \"id\": 3,\n" +
        "                    \"name\": \"feature_with_string_value\",\n" +
        "                    \"type\": \"STANDARD\"\n" +
        "                }\n" +
        "            }\n" +
        "        ]\n" +
        "    }";

    JsonNode node = MapperFactory.getMapper().readTree(json);
    EnvironmentModel environmentModel = EnvironmentModel.load(node, EnvironmentModel.class);

    Assertions.assertNotNull(environmentModel);
    Assertions.assertTrue(environmentModel.getId() == 1);
    Assertions.assertEquals(environmentModel.getApiKey(), "api-key");

    Assertions.assertNotNull(environmentModel.getProject());
    Assertions.assertNotNull(environmentModel.getFeatureStates());

    Assertions.assertTrue(environmentModel.getFeatureStates().size() == 3);

    FeatureStateModel featureState = FeatureStateHelper.getFeatureStateForFeatureByName(
        environmentModel.getFeatureStates(),
        "feature_with_string_value"
    );

    Assertions.assertNotNull(featureState);
  }

  @Test
  public void test_build_environment_model_with_multivariate_flag() throws Exception {
    String json = "{\n" +
        "    \"id\": 1,\n" +
        "    \"api_key\": \"api-key\",\n" +
        "    \"project\": {\n" +
        "        \"id\": 1,\n" +
        "        \"name\": \"test project\",\n" +
        "        \"organisation\": {\n" +
        "            \"id\": 1,\n" +
        "            \"name\": \"Test Org\",\n" +
        "            \"stop_serving_flags\": false,\n" +
        "            \"persist_trait_data\": true,\n" +
        "            \"feature_analytics\": true\n" +
        "        },\n" +
        "        \"hide_disabled_flags\": false\n" +
        "    },\n" +
        "    \"feature_states\": [\n" +
        "        {\n" +
        "            \"id\": 1,\n" +
        "            \"enabled\": true,\n" +
        "            \"feature_state_value\": null,\n" +
        "            \"feature\": {\n" +
        "                \"id\": 1,\n" +
        "                \"name\": \"enabled_feature\",\n" +
        "                \"type\": \"STANDARD\"\n" +
        "            },\n" +
        "            \"multivariate_feature_state_values\": [\n" +
        "                {\n" +
        "                    \"id\": 1,\n" +
        "                    \"percentage_allocation\": 10.0,\n" +
        "                    \"multivariate_feature_option\": {\n" +
        "                        \"value\": \"value-1\"\n" +
        "                    }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"id\": 2,\n" +
        "                    \"percentage_allocation\": 10.0,\n" +
        "                    \"multivariate_feature_option\": {\n" +
        "                        \"value\": \"value-2\",\n" +
        "                        \"id\": 2\n" +
        "                    }\n" +
        "                }\n" +
        "            ]\n" +
        "        }\n" +
        "    ]\n" +
        "}\n";

    JsonNode node = MapperFactory.getMapper().readTree(json);
    EnvironmentModel environmentModel = EnvironmentModel.load(node, EnvironmentModel.class);

    Assertions.assertNotNull(environmentModel);

    Assertions.assertNotNull(environmentModel.getFeatureStates());
    Assertions.assertEquals(environmentModel.getFeatureStates().size(), 1);

    FeatureStateModel featureState = environmentModel.getFeatureStates().get(0);
    Assertions.assertNotNull(featureState.getMultivariateFeatureStateValues());
    Assertions.assertEquals(featureState.getMultivariateFeatureStateValues().size(), 2);
  }
}
