package com.flagsmith.flagengine.segments;

import com.flagsmith.IdentityTraits;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.utils.Hashing;
import com.flagsmith.flagengine.utils.types.TypeCasting;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SegmentEvaluator {

    public static List<SegmentModel> getIdentitySegments(EnvironmentModel environment, IdentityModel identity) {
        return getIdentitySegments(environment, identity, null);
    }

    public static List<SegmentModel> getIdentitySegments(EnvironmentModel environment, IdentityModel identity,
                                                         List<TraitModel> overrideTraits) {
        return environment
                .getProject()
                .getSegments()
                .stream()
                .filter((segment) -> evaluateIdentityInSegment(identity, segment, overrideTraits))
                .collect(Collectors.toList());
    }

    public static Boolean evaluateIdentityInSegment(IdentityModel identity, SegmentModel segment,
                                                    List<TraitModel> overrideTraits) {
        List<SegmentRuleModel> segmentRules = segment.getRules();
        List<TraitModel> traits = overrideTraits != null ? overrideTraits : identity.getIdentityTraits();

        return segmentRules != null && segmentRules.size() > 0 && segmentRules.stream().allMatch(
            (rule) -> traitsMatchSegmentRule(
                traits,
                rule,
                segment.getId(),
                identity.getCompositeKey()
            )
        );
    }

    private static Boolean traitsMatchSegmentRule(List<TraitModel> identityTraits, SegmentRuleModel rule,
                                                  Integer segmentId, String identityId) {
        Boolean matchingCondition = Boolean.TRUE;

        if (rule.getConditions() != null && rule.getConditions().size() > 0) {
            matchingCondition = rule.matchingFunction(
                rule.getConditions().stream(),
                (condition) -> traitsMatchSegmentCondition(identityTraits, condition, segmentId, identityId)
            );
        }

        List<SegmentRuleModel> rules = rule.getRules();

        return matchingCondition
            && rules != null
            && rules.stream().allMatch((segmentRule) -> traitsMatchSegmentRule(
                identityTraits,
                segmentRule,
                segmentId,
                identityId
        ));
    }

    private static Boolean traitsMatchSegmentCondition(List<TraitModel> identityTraits, SegmentConditionModel condition,
                                                       Integer segmentId, String identityId) {
        if (condition.getOperator().equals(SegmentConditions.PERCENTAGE_SPLIT)) {
            try {
                Float floatValue = Float.parseFloat(condition.getValue());
                return Hashing.getHashedPercentageForObjectIds(Arrays.asList(segmentId.toString(), identityId)) <= floatValue;

            } catch (NumberFormatException nfe) {
                return Boolean.FALSE;
            }
        }

        Optional<TraitModel> matchingTrait = identityTraits
                .stream()
                .filter((trait) -> trait.getTraitKey().equals(condition.getProperty_()))
                .findFirst();

        if (matchingTrait.isPresent()) {
            return traitsMatchValue(condition, matchingTrait.get().getTraitValue());
        }

        return false;
    }

    private static Boolean traitsMatchValue(SegmentConditionModel condition, Object value) {
        SegmentConditions operator = condition.getOperator();
        if (operator.equals(SegmentConditions.NOT_CONTAINS)) {
            return ((String) value).indexOf(condition.getValue()) > -1;
        } else if (operator.equals(SegmentConditions.REGEX)) {
            Pattern pattern = Pattern.compile(condition.getValue());
            return pattern.matcher((String) value).find();
        } else {
            return TypeCasting.compare(operator, condition.getValue(), value);
        }
    }


}
