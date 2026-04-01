package com.flagsmith.flagengine.unit.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.FeatureContext;
import com.flagsmith.flagengine.FeatureValue;
import com.flagsmith.flagengine.Traits;
import com.flagsmith.mappers.EngineMappers;
import com.flagsmith.models.TraitConfig;
import com.flagsmith.models.environments.EnvironmentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.flagsmith.FlagsmithTestHelper;

public class EngineMappersTest {
  private static Stream<Arguments> expectedTraitMaps() {
    return Stream.of(
        Arguments.argumentSet(
          "no transiency data",
          Map.of("test", 1)
        ),
        Arguments.argumentSet(
          "with transiency data",
          Map.of("test", TraitConfig.fromObject(1))
        )
    );
  }

  @ParameterizedTest
  @MethodSource("expectedTraitMaps")
  public void testMapContextAndIdentityDataToContext_returnsExpectedContext(
    Map<String, Object> expectedTraitMap
  ) {
    // Arrange
    final String identifier = "test-identifier";
    final EvaluationContext context = FlagsmithTestHelper.evaluationContext();
    final Traits expectedTraits = new Traits().withAdditionalProperty("test", 1);

    // Act
    final EvaluationContext mappedContext = EngineMappers.mapContextAndIdentityDataToContext(
      context, identifier, expectedTraitMap);

    // Assert
    assertEquals(
      expectedTraits.getAdditionalProperties(),
      mappedContext.getIdentity().getTraits().getAdditionalProperties());
  }

  @Test
  public void testMapEnvironmentToContext_preservesMultivariateValueTypes()
      throws JsonProcessingException {
    String environmentJson = "{\n"
        + "  \"api_key\": \"test-key\",\n"
        + "  \"name\": \"Test\",\n"
        + "  \"project\": {\n"
        + "    \"name\": \"Test project\",\n"
        + "    \"organisation\": {\n"
        + "      \"feature_analytics\": false,\n"
        + "      \"name\": \"Test Org\",\n"
        + "      \"id\": 1,\n"
        + "      \"persist_trait_data\": true,\n"
        + "      \"stop_serving_flags\": false\n"
        + "    },\n"
        + "    \"id\": 1,\n"
        + "    \"hide_disabled_flags\": false,\n"
        + "    \"segments\": []\n"
        + "  },\n"
        + "  \"segment_overrides\": [],\n"
        + "  \"id\": 1,\n"
        + "  \"feature_states\": [\n"
        + "    {\n"
        + "      \"feature_state_value\": true,\n"
        + "      \"django_id\": 1,\n"
        + "      \"featurestate_uuid\": \"40eb539d-3713-4720-bbd4-829dbef10d51\",\n"
        + "      \"feature\": { \"name\": \"mv_feature\", \"type\": \"MULTIVARIATE\", \"id\": 1 },\n"
        + "      \"enabled\": true,\n"
        + "      \"multivariate_feature_state_values\": [\n"
        + "        {\n"
        + "          \"id\": 1,\n"
        + "          \"multivariate_feature_option\": { \"value\": false },\n"
        + "          \"percentage_allocation\": 50.0,\n"
        + "          \"mv_fs_value_uuid\": \"808cba14-03ca-4835-a7f7-58387f01f87d\"\n"
        + "        },\n"
        + "        {\n"
        + "          \"id\": 2,\n"
        + "          \"multivariate_feature_option\": { \"value\": 42 },\n"
        + "          \"percentage_allocation\": 30.0,\n"
        + "          \"mv_fs_value_uuid\": \"918dbb25-14db-4946-b8a8-69488f02f98e\"\n"
        + "        },\n"
        + "        {\n"
        + "          \"id\": 3,\n"
        + "          \"multivariate_feature_option\": { \"value\": \"a string\" },\n"
        + "          \"percentage_allocation\": 20.0,\n"
        + "          \"mv_fs_value_uuid\": \"a29eca36-25dc-5057-c9b9-7a599f13g09f\"\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  ],\n"
        + "  \"identity_overrides\": []\n"
        + "}";

    EnvironmentModel env = MapperFactory.getMapper()
        .readValue(environmentJson, EnvironmentModel.class);

    EvaluationContext context = EngineMappers.mapEnvironmentToContext(env);

    FeatureContext feature = (FeatureContext) context.getFeatures()
        .getAdditionalProperties().get("mv_feature");
    List<FeatureValue> variants = feature.getVariants();

    assertEquals(3, variants.size());

    assertInstanceOf(Boolean.class, feature.getValue(),
        "Control value should be Boolean, not " + feature.getValue().getClass().getName());
    assertEquals(true, feature.getValue());

    assertInstanceOf(Boolean.class, variants.get(0).getValue(),
        "Boolean variant value should be Boolean, not " + variants.get(0).getValue().getClass().getName());
    assertEquals(false, variants.get(0).getValue());

    assertInstanceOf(Integer.class, variants.get(1).getValue(),
        "Integer variant value should be Integer, not " + variants.get(1).getValue().getClass().getName());
    assertEquals(42, variants.get(1).getValue());

    assertInstanceOf(String.class, variants.get(2).getValue(),
        "String variant value should be String, not " + variants.get(2).getValue().getClass().getName());
    assertEquals("a string", variants.get(2).getValue());
  }
}
