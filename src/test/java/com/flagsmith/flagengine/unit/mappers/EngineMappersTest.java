package com.flagsmith.flagengine.unit.mappers;

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

import java.io.IOException;
import java.io.InputStream;
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
      throws IOException {
    InputStream json = getClass().getResourceAsStream("multivariate_environment.json");
    EnvironmentModel env = MapperFactory.getMapper()
        .readValue(json, EnvironmentModel.class);

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
