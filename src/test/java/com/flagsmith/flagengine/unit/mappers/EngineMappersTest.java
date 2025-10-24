package com.flagsmith.flagengine.unit.mappers;

import com.flagsmith.mappers.EngineMappers;
import com.flagsmith.models.TraitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.flagsmith.FlagsmithTestHelper;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.Traits;

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
}
