package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.SegmentModel;

import static com.flagsmith.flagengine.unit.segments.IdentitySegmentFixtures.*;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
}
