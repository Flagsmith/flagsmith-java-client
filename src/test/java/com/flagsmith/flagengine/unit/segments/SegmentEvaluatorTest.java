package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.SegmentCondition;
import com.flagsmith.flagengine.SegmentContext;
import com.flagsmith.flagengine.SegmentRule;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.mappers.EngineMappers;
import com.flagsmith.models.TraitModel;

import static com.flagsmith.flagengine.unit.segments.IdentitySegmentFixtures.*;
import com.flagsmith.FlagsmithTestHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class SegmentEvaluatorTest {

  private static Stream<Arguments> identitiesInSegments() {
    return Stream.of(
        Arguments.of(emptySegment(), emptyIdentityTraits(), Boolean.FALSE),
        Arguments.of(segmentSingleCondition(), emptyIdentityTraits(), Boolean.FALSE),
        Arguments.of(segmentSingleCondition(), oneIdentityTrait(), Boolean.TRUE),
        Arguments.of(segmentMultipleConditionsAll(), emptyIdentityTraits(), Boolean.FALSE),
        Arguments.of(segmentMultipleConditionsAll(), oneIdentityTrait(), Boolean.FALSE),
        Arguments.of(segmentMultipleConditionsAll(), twoIdentityTraits(), Boolean.TRUE),
        Arguments.of(segmentMultipleConditionsAny(), emptyIdentityTraits(), Boolean.FALSE),
        Arguments.of(segmentMultipleConditionsAny(), Arrays.asList(secondIdentityTrait()),
            Boolean.TRUE),
        Arguments.of(segmentMultipleConditionsAny(), oneIdentityTrait(), Boolean.TRUE),
        Arguments.of(segmentMultipleConditionsAny(), twoIdentityTraits(), Boolean.TRUE),
        Arguments.of(segmentNestedRules(), emptyIdentityTraits(), Boolean.FALSE),
        Arguments.of(segmentNestedRules(), oneIdentityTrait(), Boolean.FALSE),
        Arguments.of(segmentNestedRules(), threeIdentityTraits(), Boolean.TRUE),
        Arguments.of(segmentConditionsAndNestedRules(), emptyIdentityTraits(), Boolean.FALSE),
        Arguments.of(segmentConditionsAndNestedRules(), oneIdentityTrait(), Boolean.FALSE),
        Arguments.of(segmentConditionsAndNestedRules(), threeIdentityTraits(), Boolean.TRUE));
  }

  @ParameterizedTest
  @MethodSource("identitiesInSegments")
  public void testContextInSegment(SegmentContext segment, List<TraitModel> identityTraits,
      Boolean expectedResponse) {

    final EvaluationContext context = EngineMappers.mapContextAndIdentityDataToContext(
        FlagsmithTestHelper.evaluationContext(), "foo",
        identityTraits.stream().collect(
            java.util.stream.Collectors.toMap(TraitModel::getTraitKey, TraitModel::getTraitValue)));

    Boolean actualResult = SegmentEvaluator.isContextInSegment(context, segment);

    Assertions.assertEquals(actualResult, expectedResponse);
  }

  private static Stream<Arguments> traitExistenceChecks() {
    return Stream.of(
        Arguments.of(SegmentConditions.IS_SET, "foo", new ArrayList<>(), false),
        Arguments.of(SegmentConditions.IS_NOT_SET, "foo", new ArrayList<>(), true),
        Arguments.of(SegmentConditions.IS_SET, "foo", new ArrayList<>(Arrays.asList(
            new TraitModel("foo", "bar"))), true),
        Arguments.of(SegmentConditions.IS_NOT_SET, "foo", new ArrayList<>(Arrays.asList(
            new TraitModel("foo", "bar"))), false));
  }

  @ParameterizedTest
  @MethodSource("traitExistenceChecks")
  public void testTraitExistenceConditions(SegmentConditions conditionOperator, String conditionProperty,
      List<TraitModel> traitModels, Boolean expectedResult) {
    // Given
    // An identity to test with which has the traits as defined in the DataProvider
    final EvaluationContext context = EngineMappers.mapContextAndIdentityDataToContext(
        FlagsmithTestHelper.evaluationContext(), "foo",
        traitModels.stream().collect(
            java.util.stream.Collectors.toMap(TraitModel::getTraitKey, TraitModel::getTraitValue)));

    // And a segment which has the operator and property value as defined in the
    // DataProvider
    SegmentContext segment = new SegmentContext().withName("testSegment").withRules(
        Arrays.asList(new SegmentRule().withType(SegmentRule.Type.ALL).withConditions(
            Arrays.asList(new SegmentCondition()
                .withOperator(conditionOperator)
                .withProperty(conditionProperty)))));

    // When
    // We evaluate whether the identity is in the segment
    Boolean inSegment = SegmentEvaluator.isContextInSegment(context, segment);

    // Then
    // The result is as we expect from the DataProvider definition
    Assertions.assertEquals(inSegment, expectedResult);
  }
}
