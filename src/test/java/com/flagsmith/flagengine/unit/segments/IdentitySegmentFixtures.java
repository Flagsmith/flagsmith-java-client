package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.SegmentConditionModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.segments.SegmentRuleModel;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.segments.constants.SegmentRules;

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

    public static SegmentModel emptySegment() {
        SegmentModel segment = new SegmentModel();
        segment.setId(1);
        segment.setName("empty_segment");

        return segment;
    }

    public static SegmentModel segmentSingleCondition() {
        SegmentConditionModel segmentCondition = new SegmentConditionModel();
        segmentCondition.setOperator(SegmentConditions.EQUAL);
        segmentCondition.setProperty_(traitKey1);
        segmentCondition.setValue(traitValue1);

        SegmentRuleModel segmentRule = new SegmentRuleModel();
        segmentRule.setType(SegmentRules.ALL_RULE.getRule());
        segmentRule.setConditions(Arrays.asList(segmentCondition));

        SegmentModel segment = new SegmentModel();
        segment.setId(2);
        segment.setName("segment_one_condition");
        segment.setRules(Arrays.asList(segmentRule));

        return segment;
    }

    public static SegmentModel segmentMultipleConditionsAll() {
        SegmentConditionModel segmentCondition = new SegmentConditionModel();
        segmentCondition.setOperator(SegmentConditions.EQUAL);
        segmentCondition.setProperty_(traitKey1);
        segmentCondition.setValue(traitValue1);

        SegmentConditionModel segmentCondition2 = new SegmentConditionModel();
        segmentCondition2.setOperator(SegmentConditions.EQUAL);
        segmentCondition2.setProperty_(traitKey2);
        segmentCondition2.setValue(traitValue2);

        SegmentRuleModel segmentRule = new SegmentRuleModel();
        segmentRule.setType(SegmentRules.ALL_RULE.getRule());
        segmentRule.setConditions(Arrays.asList(segmentCondition, segmentCondition2));

        SegmentModel segment = new SegmentModel();
        segment.setId(3);
        segment.setName("segment_multiple_conditions_all");
        segment.setRules(Arrays.asList(segmentRule));

        return segment;
    }

    public static SegmentModel segmentMultipleConditionsAny() {
        SegmentConditionModel segmentCondition = new SegmentConditionModel();
        segmentCondition.setOperator(SegmentConditions.EQUAL);
        segmentCondition.setProperty_(traitKey1);
        segmentCondition.setValue(traitValue1);

        SegmentConditionModel segmentCondition2 = new SegmentConditionModel();
        segmentCondition2.setOperator(SegmentConditions.EQUAL);
        segmentCondition2.setProperty_(traitKey2);
        segmentCondition2.setValue(traitValue2);

        SegmentRuleModel segmentRule = new SegmentRuleModel();
        segmentRule.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule.setConditions(Arrays.asList(segmentCondition, segmentCondition2));

        SegmentModel segment = new SegmentModel();
        segment.setId(4);
        segment.setName("segment_multiple_conditions_any");
        segment.setRules(Arrays.asList(segmentRule));

        return segment;
    }

    public static SegmentModel segmentNestedRules() {
        SegmentConditionModel segmentCondition = new SegmentConditionModel();
        segmentCondition.setOperator(SegmentConditions.EQUAL);
        segmentCondition.setProperty_(traitKey1);
        segmentCondition.setValue(traitValue1);

        SegmentConditionModel segmentCondition2 = new SegmentConditionModel();
        segmentCondition2.setOperator(SegmentConditions.EQUAL);
        segmentCondition2.setProperty_(traitKey2);
        segmentCondition2.setValue(traitValue2);

        SegmentConditionModel segmentCondition3 = new SegmentConditionModel();
        segmentCondition3.setOperator(SegmentConditions.EQUAL);
        segmentCondition3.setProperty_(traitKey3);
        segmentCondition3.setValue(traitValue3);

        SegmentRuleModel segmentRule = new SegmentRuleModel();
        segmentRule.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule.setConditions(Arrays.asList(segmentCondition, segmentCondition2));

        SegmentRuleModel segmentRule2 = new SegmentRuleModel();
        segmentRule2.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule2.setConditions(Arrays.asList(segmentCondition3));

        SegmentRuleModel segmentRule3 = new SegmentRuleModel();
        segmentRule3.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule3.setRules(Arrays.asList(segmentRule, segmentRule2));

        SegmentModel segment = new SegmentModel();
        segment.setId(5);
        segment.setName("segment_nested_rules_all");
        segment.setRules(Arrays.asList(segmentRule3));

        return segment;
    }

    public static SegmentModel segmentConditionsAndNestedRules() {
        SegmentConditionModel segmentCondition = new SegmentConditionModel();
        segmentCondition.setOperator(SegmentConditions.EQUAL);
        segmentCondition.setProperty_(traitKey1);
        segmentCondition.setValue(traitValue1);

        SegmentConditionModel segmentCondition2 = new SegmentConditionModel();
        segmentCondition2.setOperator(SegmentConditions.EQUAL);
        segmentCondition2.setProperty_(traitKey2);
        segmentCondition2.setValue(traitValue2);

        SegmentConditionModel segmentCondition3 = new SegmentConditionModel();
        segmentCondition3.setOperator(SegmentConditions.EQUAL);
        segmentCondition3.setProperty_(traitKey3);
        segmentCondition3.setValue(traitValue3);

        SegmentRuleModel segmentRule = new SegmentRuleModel();
        segmentRule.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule.setConditions(Arrays.asList(segmentCondition));

        SegmentRuleModel segmentRule2 = new SegmentRuleModel();
        segmentRule2.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule2.setConditions(Arrays.asList(segmentCondition2));

        SegmentRuleModel segmentRule3 = new SegmentRuleModel();
        segmentRule3.setType(SegmentRules.ANY_RULE.getRule());
        segmentRule3.setConditions(Arrays.asList(segmentCondition3));

        segmentRule.setRules(Arrays.asList(segmentRule2, segmentRule3));

        SegmentModel segment = new SegmentModel();
        segment.setId(6);
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
