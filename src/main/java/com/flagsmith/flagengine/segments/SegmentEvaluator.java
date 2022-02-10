package com.flagsmith.flagengine.segments;

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

  /**
   * Get segment identities from environment and identity.
   * @param environment Environment instance.
   * @param identity Identity Instance.
   * @return
   */
  public static List<SegmentModel> getIdentitySegments(EnvironmentModel environment,
                                                       IdentityModel identity) {
    return getIdentitySegments(environment, identity, null);
  }

  /**
   * Get segment identities from environment and identity along with traits to override.
   * @param environment Environment Instance.
   * @param identity Identity Instance.
   * @param overrideTraits Traits to over ride.
   * @return
   */
  public static List<SegmentModel> getIdentitySegments(EnvironmentModel environment,
                                                       IdentityModel identity,
                                                       List<TraitModel> overrideTraits) {
    return environment
        .getProject()
        .getSegments()
        .stream()
        .filter((segment) -> evaluateIdentityInSegment(identity, segment, overrideTraits))
        .collect(Collectors.toList());
  }

  /**
   * Evaluate the traits in identities and overrides with rules from segments.
   * @param identity Identity instance.
   * @param segment Segment Instance.
   * @param overrideTraits Overriden traits.
   * @return
   */
  public static Boolean evaluateIdentityInSegment(IdentityModel identity, SegmentModel segment,
                                                  List<TraitModel> overrideTraits) {
    List<SegmentRuleModel> segmentRules = segment.getRules();
    List<TraitModel> traits =
        overrideTraits != null ? overrideTraits : identity.getIdentityTraits();

    if (segmentRules != null && segmentRules.size() > 0) {
      List<Boolean> segmentRuleEvaluations = segmentRules.stream().map(
          (rule) -> traitsMatchSegmentRule(
              traits,
              rule,
              segment.getId(),
              identity.getCompositeKey()
          )
      ).collect(Collectors.toList());

      return segmentRuleEvaluations.stream().allMatch((bool) -> bool);
    }

    return Boolean.FALSE;
  }

  /**
   * Evaluate whether the trait match the rule from segment.
   * @param identityTraits Traits to match against.
   * @param rule Rule from segments to evaluate with.
   * @param segmentId Segment ID (for hashing)
   * @param identityId Identity ID (for hashing)
   * @return
   */
  private static Boolean traitsMatchSegmentRule(List<TraitModel> identityTraits,
                                                SegmentRuleModel rule,
                                                Integer segmentId, String identityId) {
    Boolean matchingCondition = Boolean.TRUE;

    if (rule.getConditions() != null && rule.getConditions().size() > 0) {
      List<Boolean> conditionEvaluations = rule.getConditions().stream()
          .map((condition) -> traitsMatchSegmentCondition(identityTraits, condition, segmentId,
              identityId))
          .collect(Collectors.toList());

      matchingCondition = rule.matchingFunction(
          conditionEvaluations.stream()
      );
    }

    List<SegmentRuleModel> rules = rule.getRules();

    if (rules != null) {
      matchingCondition = matchingCondition && rules.stream()
          .allMatch((segmentRule) -> traitsMatchSegmentRule(
              identityTraits,
              segmentRule,
              segmentId,
              identityId
          ));
    }

    return matchingCondition;
  }

  /**
   * Evaluate traits and compare them with condition.
   * @param identityTraits Traits to match against.
   * @param condition Condition to evaluate with.
   * @param segmentId Segment ID (for hashing)
   * @param identityId Identity ID (for hashing)
   * @return
   */
  private static Boolean traitsMatchSegmentCondition(List<TraitModel> identityTraits,
                                                     SegmentConditionModel condition,
                                                     Integer segmentId, String identityId) {
    if (condition.getOperator().equals(SegmentConditions.PERCENTAGE_SPLIT)) {
      try {
        Float floatValue = Float.parseFloat(condition.getValue());
        return Hashing.getInstance().getHashedPercentageForObjectIds(
            Arrays.asList(segmentId.toString(), identityId)) <= floatValue;

      } catch (NumberFormatException nfe) {
        return Boolean.FALSE;
      }
    }

    if (identityTraits != null) {
      Optional<TraitModel> matchingTrait = identityTraits
          .stream()
          .filter((trait) -> trait.getTraitKey().equals(condition.getProperty_()))
          .findFirst();

      if (matchingTrait.isPresent()) {
        return traitsMatchValue(condition, matchingTrait.get().getTraitValue());
      }
    }

    return false;
  }

  /**
   * Matches condition value with the trait value.
   * @param condition Condition to evaluate with.
   * @param value Trait value to compare with.
   * @return
   */
  private static Boolean traitsMatchValue(SegmentConditionModel condition, Object value) {
    SegmentConditions operator = condition.getOperator();
    if (operator.equals(SegmentConditions.NOT_CONTAINS)) {
      return ((String) value).indexOf(condition.getValue()) > -1;
    } else if (operator.equals(SegmentConditions.REGEX)) {
      Pattern pattern = Pattern.compile(condition.getValue());
      return pattern.matcher((String) value).find();
    } else {
      return TypeCasting.compare(operator, value, condition.getValue());
    }
  }
}
