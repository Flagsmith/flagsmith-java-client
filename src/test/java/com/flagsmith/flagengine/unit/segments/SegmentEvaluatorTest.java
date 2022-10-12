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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Test(groups = "unit")
public class SegmentEvaluatorTest {

  @DataProvider(name = "identitiesInSegments")
  public Object[][] identitiesInSegments() {
    return new Object[][] {
        new Object[] {emptySegment(), emptyIdentityTraits(), Boolean.FALSE},
        new Object[] {segmentSingleCondition(), emptyIdentityTraits(), Boolean.FALSE},
        new Object[] {segmentSingleCondition(), oneIdentityTrait(), Boolean.TRUE},
        new Object[] {segmentMultipleConditionsAll(), emptyIdentityTraits(), Boolean.FALSE},
        new Object[] {segmentMultipleConditionsAll(), oneIdentityTrait(), Boolean.FALSE},
        new Object[] {segmentMultipleConditionsAll(), twoIdentityTraits(), Boolean.TRUE},
        new Object[] {segmentMultipleConditionsAny(), emptyIdentityTraits(), Boolean.FALSE},
        new Object[] {segmentMultipleConditionsAny(), Arrays.asList(secondIdentityTrait()),
            Boolean.TRUE},
        new Object[] {segmentMultipleConditionsAny(), oneIdentityTrait(), Boolean.TRUE},
        new Object[] {segmentMultipleConditionsAny(), twoIdentityTraits(), Boolean.TRUE},
        new Object[] {segmentNestedRules(), emptyIdentityTraits(), Boolean.FALSE},
        new Object[] {segmentNestedRules(), oneIdentityTrait(), Boolean.FALSE},
        new Object[] {segmentNestedRules(), threeIdentityTraits(), Boolean.TRUE},
        new Object[] {segmentConditionsAndNestedRules(), emptyIdentityTraits(), Boolean.FALSE},
        new Object[] {segmentConditionsAndNestedRules(), oneIdentityTrait(), Boolean.FALSE},
        new Object[] {segmentConditionsAndNestedRules(), threeIdentityTraits(), Boolean.TRUE},
    };
  }

  @Test(dataProvider = "identitiesInSegments")
  public void testIdentityInSegment(SegmentModel segment, List<TraitModel> identityTraits,
                                    Boolean expectedResponse) {
    IdentityModel mockIdentity = new IdentityModel();
    mockIdentity.setIdentifier("foo");
    mockIdentity.setIdentityTraits(identityTraits);
    mockIdentity.setEnvironmentApiKey("api-key");

    Boolean actualResult = SegmentEvaluator.evaluateIdentityInSegment(mockIdentity, segment, null);

    Assert.assertTrue(actualResult.equals(expectedResponse));
  }

  @DataProvider(name = "traitExistenceChecks")
  public Object[][] traitExistenceChecks() {
    return new Object[][] {
      new Object[] {SegmentConditions.IS_SET, "foo", new ArrayList<>(), false},
      new Object[] {SegmentConditions.IS_NOT_SET, "foo", new ArrayList<>(), true},
      new Object[] {SegmentConditions.IS_SET, "foo", new ArrayList<>(Arrays.asList(new TraitModel("foo", "bar"))), true},
      new Object[] {SegmentConditions.IS_NOT_SET, "foo", new ArrayList<>(Arrays.asList(new TraitModel("foo", "bar"))), false},
    };
  }

  @Test(dataProvider = "traitExistenceChecks")
  public void testTraitExistenceConditions(SegmentConditions conditionOperator, String conditionProperty, List<TraitModel> traitModels, Boolean expectedResult) {
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
    Assert.assertEquals(inSegment, expectedResult);
  }
}
