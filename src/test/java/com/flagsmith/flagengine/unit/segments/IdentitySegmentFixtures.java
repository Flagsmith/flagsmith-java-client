package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.SegmentContext;
import com.flagsmith.flagengine.SegmentCondition;
import com.flagsmith.flagengine.SegmentRule;
import com.flagsmith.models.TraitModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IdentitySegmentFixtures {

  public static final String traitKey1 = "email";
  public static final String traitValue1 = "user@example.com";

  public static final String traitKey2 = "num_purchase";
  public static final String traitValue2 = "12";

  public static final String traitKey3 = "date_joined";
  public static final String traitValue3 = "2021-01-01";

  public static SegmentContext emptySegment() {
    return new SegmentContext().withKey("1").withName("empty_segment");
  }

  public static SegmentContext segmentSingleCondition() {
    return new SegmentContext().withKey("2").withName("segment_one_condition")
        .withRules(
            Arrays.asList(
                new SegmentRule().withType(SegmentRule.Type.ALL).withConditions(
                    Arrays.asList(
                        new SegmentCondition()
                            .withOperator(SegmentConditions.EQUAL)
                            .withProperty(traitKey1)
                            .withValue(traitValue1)))));
  }

  public static SegmentContext segmentMultipleConditionsAll() {
    SegmentCondition segmentCondition = new SegmentCondition();
    segmentCondition.setOperator(SegmentConditions.EQUAL);
    segmentCondition.setProperty(traitKey1);
    segmentCondition.setValue(traitValue1);

    SegmentCondition segmentCondition2 = new SegmentCondition();
    segmentCondition2.setOperator(SegmentConditions.EQUAL);
    segmentCondition2.setProperty(traitKey2);
    segmentCondition2.setValue(traitValue2);

    SegmentRule segmentRule = new SegmentRule();
    segmentRule.setType(SegmentRule.Type.ALL);
    segmentRule.setConditions(Arrays.asList(segmentCondition, segmentCondition2));

    SegmentContext segment = new SegmentContext();
    segment.setKey("3");
    segment.setName("segment_multiple_conditions_all");
    segment.setRules(Arrays.asList(segmentRule));

    return segment;
  }

  public static SegmentContext segmentMultipleConditionsAny() {
    SegmentCondition segmentCondition = new SegmentCondition();
    segmentCondition.setOperator(SegmentConditions.EQUAL);
    segmentCondition.setProperty(traitKey1);
    segmentCondition.setValue(traitValue1);

    SegmentCondition segmentCondition2 = new SegmentCondition();
    segmentCondition2.setOperator(SegmentConditions.EQUAL);
    segmentCondition2.setProperty(traitKey2);
    segmentCondition2.setValue(traitValue2);

    SegmentRule segmentRule = new SegmentRule();
    segmentRule.setType(SegmentRule.Type.ANY);
    segmentRule.setConditions(Arrays.asList(segmentCondition, segmentCondition2));

    SegmentContext segment = new SegmentContext();
    segment.setKey("4");
    segment.setName("segment_multiple_conditions_any");
    segment.setRules(Arrays.asList(segmentRule));

    return segment;
  }

  public static SegmentContext segmentNestedRules() {
    SegmentCondition segmentCondition = new SegmentCondition();
    segmentCondition.setOperator(SegmentConditions.EQUAL);
    segmentCondition.setProperty(traitKey1);
    segmentCondition.setValue(traitValue1);

    SegmentCondition segmentCondition2 = new SegmentCondition();
    segmentCondition2.setOperator(SegmentConditions.EQUAL);
    segmentCondition2.setProperty(traitKey2);
    segmentCondition2.setValue(traitValue2);

    SegmentCondition segmentCondition3 = new SegmentCondition();
    segmentCondition3.setOperator(SegmentConditions.EQUAL);
    segmentCondition3.setProperty(traitKey3);
    segmentCondition3.setValue(traitValue3);

    SegmentRule segmentRule = new SegmentRule();
    segmentRule.setType(SegmentRule.Type.ANY);
    segmentRule.setConditions(Arrays.asList(segmentCondition, segmentCondition2));

    SegmentRule segmentRule2 = new SegmentRule();
    segmentRule2.setType(SegmentRule.Type.ANY);
    segmentRule2.setConditions(Arrays.asList(segmentCondition3));

    SegmentRule segmentRule3 = new SegmentRule();
    segmentRule3.setType(SegmentRule.Type.ANY);
    segmentRule3.setRules(Arrays.asList(segmentRule, segmentRule2));

    SegmentContext segment = new SegmentContext();
    segment.setKey("5");
    segment.setName("segment_nested_rules_all");
    segment.setRules(Arrays.asList(segmentRule3));

    return segment;
  }

  public static SegmentContext segmentConditionsAndNestedRules() {
    SegmentCondition segmentCondition = new SegmentCondition();
    segmentCondition.setOperator(SegmentConditions.EQUAL);
    segmentCondition.setProperty(traitKey1);
    segmentCondition.setValue(traitValue1);

    SegmentCondition segmentCondition2 = new SegmentCondition();
    segmentCondition2.setOperator(SegmentConditions.EQUAL);
    segmentCondition2.setProperty(traitKey2);
    segmentCondition2.setValue(traitValue2);

    SegmentCondition segmentCondition3 = new SegmentCondition();
    segmentCondition3.setOperator(SegmentConditions.EQUAL);
    segmentCondition3.setProperty(traitKey3);
    segmentCondition3.setValue(traitValue3);

    SegmentRule segmentRule = new SegmentRule();
    segmentRule.setType(SegmentRule.Type.ANY);
    segmentRule.setConditions(Arrays.asList(segmentCondition));

    SegmentRule segmentRule2 = new SegmentRule();
    segmentRule2.setType(SegmentRule.Type.ANY);
    segmentRule2.setConditions(Arrays.asList(segmentCondition2));

    SegmentRule segmentRule3 = new SegmentRule();
    segmentRule3.setType(SegmentRule.Type.ANY);
    segmentRule3.setConditions(Arrays.asList(segmentCondition3));

    segmentRule.setRules(Arrays.asList(segmentRule2, segmentRule3));

    SegmentContext segment = new SegmentContext();
    segment.setKey("6");
    segment.setName("segment_multiple_conditions_all_and_nested_rules");
    segment.setRules(Arrays.asList(segmentRule3));

    return segment;
  }


  public static TraitModel firstIdentityTrait() {
    TraitModel trait = new TraitModel();
    trait.setTraitKey(traitKey1);
    trait.setTraitValue(traitValue1);

    return trait;
  }

  public static TraitModel secondIdentityTrait() {
    TraitModel trait = new TraitModel();
    trait.setTraitKey(traitKey2);
    trait.setTraitValue(traitValue2);

    return trait;
  }

  public static TraitModel thirdIdentityTrait() {
    TraitModel trait = new TraitModel();
    trait.setTraitKey(traitKey3);
    trait.setTraitValue(traitValue3);

    return trait;
  }

  public static List<TraitModel> emptyIdentityTraits() {
    return new ArrayList<TraitModel>();
  }

  public static List<TraitModel> oneIdentityTrait() {
    return Arrays.asList(firstIdentityTrait());
  }

  public static List<TraitModel> twoIdentityTraits() {
    return Arrays.asList(firstIdentityTrait(), secondIdentityTrait());
  }

  public static List<TraitModel> threeIdentityTraits() {
    return Arrays.asList(firstIdentityTrait(), secondIdentityTrait(), thirdIdentityTrait());
  }
}
