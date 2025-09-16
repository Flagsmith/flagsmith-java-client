package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.IdentityContext;
import com.flagsmith.flagengine.SegmentCondition;
import com.flagsmith.flagengine.SegmentContext;
import com.flagsmith.flagengine.SegmentRule;
import com.flagsmith.flagengine.Traits;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.mappers.EngineMappers;
import com.flagsmith.FlagsmithTestHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

public class SegmentModelTest {

  private static Stream<Arguments> conditionTestData() {
    return Stream.of(
        Arguments.of(SegmentConditions.EQUAL, "bar", "bar", true),
        Arguments.of(SegmentConditions.EQUAL, "bar", "baz", false),
        Arguments.of(SegmentConditions.EQUAL, 1, "1", true),
        Arguments.of(SegmentConditions.EQUAL, 1, "2", false),
        Arguments.of(SegmentConditions.EQUAL, true, "true", true),
        Arguments.of(SegmentConditions.EQUAL, false, "false", true),
        Arguments.of(SegmentConditions.EQUAL, false, "true", false),
        Arguments.of(SegmentConditions.EQUAL, true, "false", false),
        Arguments.of(SegmentConditions.EQUAL, 1.23, "1.23", true),
        Arguments.of(SegmentConditions.EQUAL, 1.23, "4.56", false),
        Arguments.of(SegmentConditions.GREATER_THAN, 2, "1", true),
        Arguments.of(SegmentConditions.GREATER_THAN, 1, "1", false),
        Arguments.of(SegmentConditions.GREATER_THAN, 0, "1", false),
        Arguments.of(SegmentConditions.GREATER_THAN, 2.1, "2.0", true),
        Arguments.of(SegmentConditions.GREATER_THAN, 2.1, "2.1", false),
        Arguments.of(SegmentConditions.GREATER_THAN, 2.0, "2.1", false),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, 2, "1", true),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, 1, "1", true),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, 0, "1", false),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, 2.1, "2.0", true),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, 2.1, "2.1", true),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, 2.0, "2.1", false),
        Arguments.of(SegmentConditions.LESS_THAN, 1, "2", true),
        Arguments.of(SegmentConditions.LESS_THAN, 1, "1", false),
        Arguments.of(SegmentConditions.LESS_THAN, 1, "0", false),
        Arguments.of(SegmentConditions.LESS_THAN, 2.0, "2.1", true),
        Arguments.of(SegmentConditions.LESS_THAN, 2.1, "2.1", false),
        Arguments.of(SegmentConditions.LESS_THAN, 2.1, "2.0", false),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, 1, "2", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, 1, "1", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, 1, "0", false),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, 2.0, "2.1", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, 2.1, "2.1", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, 2.1, "2.0", false),
        Arguments.of(SegmentConditions.NOT_EQUAL, "bar", "baz", true),
        Arguments.of(SegmentConditions.NOT_EQUAL, "bar", "bar", false),
        Arguments.of(SegmentConditions.NOT_EQUAL, 1, "2", true),
        Arguments.of(SegmentConditions.NOT_EQUAL, 1, "1", false),
        Arguments.of(SegmentConditions.NOT_EQUAL, true, "false", true),
        Arguments.of(SegmentConditions.NOT_EQUAL, false, "true", true),
        Arguments.of(SegmentConditions.NOT_EQUAL, false, "false", false),
        Arguments.of(SegmentConditions.NOT_EQUAL, true, "true", false),
        Arguments.of(SegmentConditions.CONTAINS, "bar", "b", true),
        Arguments.of(SegmentConditions.CONTAINS, "bar", "bar", true),
        Arguments.of(SegmentConditions.CONTAINS, "bar", "baz", false),
        Arguments.of(SegmentConditions.CONTAINS, 1, "2", false),
        Arguments.of(SegmentConditions.CONTAINS, 12, "1", true),
        Arguments.of(SegmentConditions.NOT_CONTAINS, "bar", "b", false),
        Arguments.of(SegmentConditions.NOT_CONTAINS, "bar", "bar", false),
        Arguments.of(SegmentConditions.NOT_CONTAINS, "bar", "baz", true),
        Arguments.of(SegmentConditions.NOT_CONTAINS, 1, "2", true),
        Arguments.of(SegmentConditions.NOT_CONTAINS, 12, "1", false),
        Arguments.of(SegmentConditions.REGEX, "foo", "[a-z]+", true),
        Arguments.of(SegmentConditions.REGEX, "FOO", "[a-z]+", false),
        Arguments.of(SegmentConditions.REGEX, 42, "[a-z]+", false),
        Arguments.of(SegmentConditions.REGEX, 42, "\\d+", true),
        Arguments.of(SegmentConditions.MODULO, 2, "2|0", true),
        Arguments.of(SegmentConditions.MODULO, 3, "2|0", false),
        Arguments.of(SegmentConditions.MODULO, 2.0, "2|0", true),
        Arguments.of(SegmentConditions.MODULO, 2.0, "2.0|0.0", true),
        Arguments.of(SegmentConditions.MODULO, "foo", "2|0", false),
        Arguments.of(SegmentConditions.MODULO, "foo", "foo|bar", false),
        Arguments.of(SegmentConditions.IN, "foo", "", false),
        Arguments.of(SegmentConditions.IN, "foo", "foo,bar", true),
        Arguments.of(SegmentConditions.IN, "bar", "foo,bar", true),
        Arguments.of(SegmentConditions.IN, "ba", "foo,bar", false),
        Arguments.of(SegmentConditions.IN, "foo", "foo", true),
        Arguments.of(SegmentConditions.IN, 1, "1,2,3,4", true),
        Arguments.of(SegmentConditions.IN, 1, "", false),
        Arguments.of(SegmentConditions.IN, 1, "1", true),
        Arguments.of(SegmentConditions.IN, 1, "[1]", true),
        Arguments.of(SegmentConditions.IN, 1, "[\"1\"]", true),
        Arguments.of(SegmentConditions.IN, "bar", "[\"bar\"]", true),
        Arguments.of(SegmentConditions.IN, "bar", Arrays.asList("bar", "foo"), true),
        Arguments.of(SegmentConditions.IN, 1.5, "1.5", true),
        // Flagsmith's engine does not evaluate `IN` condition for booleans
        // due to ambiguous serialization across supported platforms.
        Arguments.of(SegmentConditions.IN, false, "false", false)
    );
  }

  @ParameterizedTest
  @MethodSource("conditionTestData")
  public void testSegmentConditionMatchesTraitValue(
      SegmentConditions condition,
      Object traitValue,
      Object conditionValue,
      Boolean expectedResponse) {

    final EvaluationContext context = EngineMappers.mapContextAndIdentityDataToContext(
        FlagsmithTestHelper.evaluationContext(), "foo",
        Collections.singletonMap("foo", traitValue));

    SegmentContext segmentContext = new SegmentContext().withKey(
        conditionValue.toString()).withRules(
            Arrays.asList(new SegmentRule().withType(SegmentRule.Type.ALL).withConditions(
                Arrays.asList(new SegmentCondition()
                    .withOperator(condition).withProperty("foo")
                    .withValue(conditionValue)))));

    Boolean actualResult = SegmentEvaluator.isContextInSegment(
        context, segmentContext);

    assertEquals(expectedResponse, actualResult);
  }

  @ParameterizedTest
  @MethodSource("semverTestData")
  public void testSemverMatchesTraitValue(
      SegmentConditions condition,
      Object traitValue,
      String conditionValue,
      Boolean expectedResponse) {

    final EvaluationContext context = EngineMappers.mapContextAndIdentityDataToContext(
        FlagsmithTestHelper.evaluationContext(), "foo",
        Collections.singletonMap("foo", traitValue));

    SegmentContext segmentContext = new SegmentContext().withKey(conditionValue).withRules(
        Arrays.asList(new SegmentRule().withType(SegmentRule.Type.ALL).withConditions(
            Arrays.asList(new SegmentCondition()
                .withOperator(condition).withProperty("foo")
                .withValue(conditionValue)))));

    Boolean actualResult = SegmentEvaluator.isContextInSegment(
        context, segmentContext);

    assertEquals(expectedResponse, actualResult);
  }

  private static Stream<Arguments> semverTestData() {
    return Stream.of(
        Arguments.of(SegmentConditions.EQUAL, "1.0.0", "1.0.0:semver", true),
        Arguments.of(SegmentConditions.EQUAL, "1.0.0", "1.0.1:semver", false),
        Arguments.of(SegmentConditions.NOT_EQUAL, "1.0.0", "1.0.0:semver", false),
        Arguments.of(SegmentConditions.NOT_EQUAL, "1.0.0", "1.0.1:semver", true),
        Arguments.of(SegmentConditions.GREATER_THAN, "1.0.1", "1.0.0:semver", true),
        Arguments.of(SegmentConditions.GREATER_THAN, "1.0.0", "1.0.0-beta:semver", true),
        Arguments.of(SegmentConditions.GREATER_THAN, "1.0.1", "1.2.0:semver", false),
        Arguments.of(SegmentConditions.GREATER_THAN, "1.0.1", "1.0.1:semver", false),
        Arguments.of(SegmentConditions.GREATER_THAN, "1.2.4", "1.2.3-pre.2+build.4:semver", true),
        Arguments.of(SegmentConditions.LESS_THAN, "1.0.0", "1.0.1:semver", true),
        Arguments.of(SegmentConditions.LESS_THAN, "1.0.0", "1.0.0:semver", false),
        Arguments.of(SegmentConditions.LESS_THAN, "1.0.1", "1.0.0:semver", false),
        Arguments.of(SegmentConditions.LESS_THAN, "1.0.0-rc.2", "1.0.0-rc.3:semver", true),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, "1.0.1", "1.0.0:semver", true),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, "1.0.1", "1.2.0:semver", false),
        Arguments.of(SegmentConditions.GREATER_THAN_INCLUSIVE, "1.0.1", "1.0.1:semver", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, "1.0.0", "1.0.1:semver", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, "1.0.0", "1.0.0:semver", true),
        Arguments.of(SegmentConditions.LESS_THAN_INCLUSIVE, "1.0.1", "1.0.0:semver", false));
  }

}
