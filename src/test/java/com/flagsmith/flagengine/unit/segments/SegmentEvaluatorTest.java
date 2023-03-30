package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.SegmentConditionModel;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.segments.SegmentRuleModel;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.segments.constants.SegmentRules;

import static com.flagsmith.flagengine.unit.segments.IdentitySegmentFixtures.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
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
        Arguments.of(segmentConditionsAndNestedRules(), threeIdentityTraits(), Boolean.TRUE)
    );
  }

  @ParameterizedTest
  @MethodSource("identitiesInSegments")
  public void testIdentityInSegment(SegmentModel segment, List<TraitModel> identityTraits,
                                    Boolean expectedResponse) {
    IdentityModel mockIdentity = new IdentityModel();
    mockIdentity.setIdentifier("foo");
    mockIdentity.setIdentityTraits(identityTraits);
    mockIdentity.setEnvironmentApiKey("api-key");

    Boolean actualResult = SegmentEvaluator.evaluateIdentityInSegment(mockIdentity, segment, null);

    Assertions.assertTrue(actualResult.equals(expectedResponse));
  }

  private static Stream<Arguments> traitExistenceChecks() {
    return Stream.of(
      Arguments.of(SegmentConditions.IS_SET, "foo", new ArrayList<>(), false),
      Arguments.of(SegmentConditions.IS_NOT_SET, "foo", new ArrayList<>(), true),
      Arguments.of(SegmentConditions.IS_SET, "foo", new ArrayList<>(Arrays.asList(
        new TraitModel("foo", "bar"))), true),
      Arguments.of(SegmentConditions.IS_NOT_SET, "foo", new ArrayList<>(Arrays.asList(
        new TraitModel("foo", "bar"))), false)
    );
  }

  @ParameterizedTest
  @MethodSource("traitExistenceChecks")
  public void testTraitExistenceConditions(SegmentConditions conditionOperator, String conditionProperty,
                                           List<TraitModel> traitModels, Boolean expectedResult) {
    // Given
    // An identity to test with which has the traits as defined in the DataProvider
    IdentityModel identityModel  = new IdentityModel();
    identityModel.setIdentifier("foo");
    identityModel.setIdentityTraits(traitModels);
    identityModel.setEnvironmentApiKey("api-key");

    // And a segment which has the operator and property value as defined in the DataProvider
    SegmentConditionModel segmentCondition = new SegmentConditionModel();
    segmentCondition.setOperator(conditionOperator);
    segmentCondition.setProperty_(conditionProperty);
    segmentCondition.setValue(null);

    SegmentRuleModel segmentRule = new SegmentRuleModel();
    segmentRule.setConditions(new ArrayList<>(Arrays.asList(segmentCondition)));
    segmentRule.setType(SegmentRules.ALL_RULE.getRule());

    SegmentModel segment = new SegmentModel();
    segment.setName("testSegment");
    segment.setRules(new ArrayList<>(Arrays.asList(segmentRule)));

    // When
    // We evaluate whether the identity is in the segment
    Boolean inSegment = SegmentEvaluator.evaluateIdentityInSegment(identityModel, segment, null);

    // Then
    // The result is as we expect from the DataProvider definition
    Assertions.assertEquals(inSegment, expectedResult);
  }
}
