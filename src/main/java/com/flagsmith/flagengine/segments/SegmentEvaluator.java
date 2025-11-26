package com.flagsmith.flagengine.segments;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.IdentityContext;
import com.flagsmith.flagengine.SegmentCondition;
import com.flagsmith.flagengine.SegmentContext;
import com.flagsmith.flagengine.SegmentRule;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.utils.Hashing;
import com.flagsmith.flagengine.utils.types.TypeCasting;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class SegmentEvaluator {
  private static ObjectMapper mapper = new ObjectMapper();
  private static Configuration jsonPathConfiguration = Configuration
      .defaultConfiguration()
      .setOptions(Option.SUPPRESS_EXCEPTIONS);
  private static TypeReference<List<String>> stringListTypeRef = new TypeReference<List<String>>() {
  };

  /**
   * Check if context is in segment.
   *
   * @param context Evaluation context.
   * @param segment Segment context.
   * @return true if context is in segment.
   */
  public static Boolean isContextInSegment(EvaluationContext context, SegmentContext segment) {
    List<SegmentRule> rules = segment.getRules();
    return !rules.isEmpty() && rules.stream()
        .allMatch((rule) -> contextMatchesRule(context, rule, segment.getKey()));
  }

  private static Boolean contextMatchesRule(EvaluationContext context, SegmentRule rule,
      String segmentKey) {
    Predicate<SegmentCondition> conditionPredicate = (condition) -> contextMatchesCondition(
        context, condition, segmentKey);

    Boolean isMatch;
    List<SegmentCondition> conditions = rule.getConditions();

    if (conditions.isEmpty()) {
      isMatch = true;
    } else {
      switch (rule.getType()) {
        case ALL:
          isMatch = conditions.stream().allMatch(conditionPredicate);
          break;
        case ANY:
          isMatch = conditions.stream().anyMatch(conditionPredicate);
          break;
        case NONE:
          isMatch = conditions.stream().noneMatch(conditionPredicate);
          break;
        default:
          return false;
      }
    }

    return isMatch && rule.getRules().stream()
        .allMatch((subRule) -> contextMatchesRule(context, subRule, segmentKey));
  }

  private static Boolean contextMatchesCondition(
      EvaluationContext context,
      SegmentCondition condition,
      String segmentKey) {
    Object contextValue = null;
    Object conditionValue = condition.getValue();
    String conditionProperty = condition.getProperty();
    SegmentConditions operator = condition.getOperator();

    if (operator == SegmentConditions.PERCENTAGE_SPLIT && StringUtils.isEmpty(conditionProperty)) {
      // Currently, the only supported condition with a blank property
      // is percentage split.
      // In this case, we use the identity key as context value.
      // This is mainly to support legacy segments created before
      // we introduced JSONPath support.
      IdentityContext identity = context.getIdentity();
      if (!(identity == null)) {
        contextValue = identity.getKey();
      }
    } else {
      contextValue = getContextValue(context, conditionProperty);
    }

    switch (operator) {
      case IN:
        if (contextValue == null || contextValue instanceof Boolean) {
          return false;
        }

        List<String> conditionList = new ArrayList<>();

        if (conditionValue instanceof List) {
          List<?> maybeConditionList = (List<?>) conditionValue;
          conditionList = maybeConditionList.stream()
              .map(Object::toString)
              .collect(Collectors.toList());
        } else if (conditionValue instanceof String) {
          String stringConditionValue = (String) conditionValue;
          try {
            // Try parsing a JSON list first
            conditionList = mapper.readValue(
                stringConditionValue, stringListTypeRef);
          } catch (IOException e) {
            // As a fallback, split by comma
            conditionList = Arrays.asList(stringConditionValue.split(","));
          }
        }

        return conditionList.contains(String.valueOf(contextValue));

      case PERCENTAGE_SPLIT:
        if (contextValue == null) {
          return false;
        }
        List<String> objectIds = List.of(segmentKey, contextValue.toString());

        final float floatValue;
        try {
          floatValue = Float.parseFloat(String.valueOf(conditionValue));
        } catch (NumberFormatException e) {
          return false;
        }

        return Hashing.getInstance()
            .getHashedPercentageForObjectIds(objectIds) <= floatValue;

      case IS_NOT_SET:
        return contextValue == null;

      case IS_SET:
        return contextValue != null;

      case CONTAINS:
        return (String.valueOf(contextValue)).indexOf(conditionValue.toString()) > -1;

      case NOT_CONTAINS:
        if (contextValue != null) {
          return (String.valueOf(contextValue)).indexOf(conditionValue.toString()) == -1;
        }
        return false;

      case REGEX:
        if (contextValue != null) {
          try {
            Pattern pattern = Pattern.compile(conditionValue.toString());
            return pattern.matcher(contextValue.toString()).find();
          } catch (PatternSyntaxException pse) {
            return false;
          }
        }
        return false;

      case MODULO:
        if (contextValue instanceof Number && conditionValue instanceof String) {
          try {
            String[] parts = conditionValue.toString().split("\\|");
            if (parts.length != 2) {
              return false;
            }
            Double divisor = Double.parseDouble(parts[0]);
            Double remainder = Double.parseDouble(parts[1]);
            Double value = ((Number) contextValue).doubleValue();
            return (value % divisor) == remainder;
          } catch (NumberFormatException nfe) {
            return false;
          }
        }
        return false;

      default:
        if (contextValue == null) {
          return false;
        }
        return TypeCasting.compare(operator, contextValue, conditionValue);
    }
  }

  /**
   * Get context value by property name.
   *
   * @param context  Evaluation context.
   * @param property Property name.
   * @return Property value.
   */
  private static Object getContextValue(EvaluationContext context, String property) {
    Object result;
    if (context.getIdentity() != null && context.getIdentity().getTraits() != null) {
      result = context.getIdentity().getTraits().getAdditionalProperties().get(property);
      if (result != null) {
        return result;
      }
    }
    if (property.startsWith("$.")) {
      result = JsonPath
          .using(jsonPathConfiguration)
          .parse(mapper.convertValue(context, Map.class))
          .read(property);
      if (result instanceof List || result instanceof Map) {
        return null;
      }
      return result;
    }
    return null;
  }
}