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
    return new SegmentContext().withKey("3").withName("segment_multiple_conditions_all")
        .withRules(
            Arrays.asList(
                new SegmentRule().withType(SegmentRule.Type.ALL).withConditions(
                    Arrays.asList(
                        new SegmentCondition()
                            .withOperator(SegmentConditions.EQUAL)
                            .withProperty(traitKey1)
                            .withValue(traitValue1),
                        new SegmentCondition()
                            .withOperator(SegmentConditions.EQUAL)
                            .withProperty(traitKey2)
                            .withValue(traitValue2)))));
  }

  public static SegmentContext segmentMultipleConditionsAny() {
    return new SegmentContext().withKey("4").withName("segment_multiple_conditions_any")
        .withRules(
            Arrays.asList(
                new SegmentRule().withType(SegmentRule.Type.ANY).withConditions(
                    Arrays.asList(
                        new SegmentCondition()
                            .withOperator(SegmentConditions.EQUAL)
                            .withProperty(traitKey1)
                            .withValue(traitValue1),
                        new SegmentCondition()
                            .withOperator(SegmentConditions.EQUAL)
                            .withProperty(traitKey2)
                            .withValue(traitValue2)))));
  }

  public static SegmentContext segmentNestedRules() {
    return new SegmentContext().withKey("5").withName("segment_nested_rules_all")
        .withRules(
            Arrays.asList(
                new SegmentRule().withType(SegmentRule.Type.ALL).withRules(
                    Arrays.asList(
                        new SegmentRule().withType(SegmentRule.Type.ANY).withConditions(
                            Arrays.asList(
                                new SegmentCondition()
                                    .withOperator(SegmentConditions.EQUAL)
                                    .withProperty(traitKey1)
                                    .withValue(traitValue1),
                                new SegmentCondition()
                                    .withOperator(SegmentConditions.EQUAL)
                                    .withProperty(traitKey2)
                                    .withValue(traitValue2))),
                        new SegmentRule().withType(SegmentRule.Type.ANY).withConditions(
                            Arrays.asList(
                                new SegmentCondition()
                                    .withOperator(SegmentConditions.EQUAL)
                                    .withProperty(traitKey3)
                                    .withValue(traitValue3)))))));
  }

  public static SegmentContext segmentConditionsAndNestedRules() {
    return new SegmentContext().withKey("6")
        .withName("segment_multiple_conditions_all_and_nested_rules")
        .withRules(
            Arrays.asList(
                new SegmentRule().withType(SegmentRule.Type.ALL).withConditions(
                    Arrays.asList(
                        new SegmentCondition()
                            .withOperator(SegmentConditions.EQUAL)
                            .withProperty(traitKey1)
                            .withValue(traitValue1)))
                    .withRules(
                        Arrays.asList(
                            new SegmentRule().withType(SegmentRule.Type.ANY).withConditions(
                                Arrays.asList(
                                    new SegmentCondition()
                                        .withOperator(SegmentConditions.EQUAL)
                                        .withProperty(traitKey2)
                                        .withValue(traitValue2))),
                            new SegmentRule().withType(SegmentRule.Type.ANY).withConditions(
                                Arrays.asList(
                                    new SegmentCondition()
                                        .withOperator(SegmentConditions.EQUAL)
                                        .withProperty(traitKey3)
                                        .withValue(traitValue3)))))));
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
