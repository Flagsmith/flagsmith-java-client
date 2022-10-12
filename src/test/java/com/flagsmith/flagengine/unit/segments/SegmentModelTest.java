package com.flagsmith.flagengine.unit.segments;

import com.flagsmith.flagengine.segments.SegmentConditionModel;
import com.flagsmith.flagengine.segments.SegmentEvaluator;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SegmentModelTest {

  @DataProvider(name = "conditionTestData")
  public Object[][] conditionTestData() {
    return new Object[][] {
        new Object[] {SegmentConditions.EQUAL, "bar", "bar", true},
        new Object[] {SegmentConditions.EQUAL, "bar", "baz", false},
        new Object[] {SegmentConditions.EQUAL, 1, "1", true},
        new Object[] {SegmentConditions.EQUAL, 1, "2", false},
        new Object[] {SegmentConditions.EQUAL, true, "true", true},
        new Object[] {SegmentConditions.EQUAL, false, "false", true},
        new Object[] {SegmentConditions.EQUAL, false, "true", false},
        new Object[] {SegmentConditions.EQUAL, true, "false", false},
        new Object[] {SegmentConditions.EQUAL, 1.23, "1.23", true},
        new Object[] {SegmentConditions.EQUAL, 1.23, "4.56", false},
        new Object[] {SegmentConditions.GREATER_THAN, 2, "1", true},
        new Object[] {SegmentConditions.GREATER_THAN, 1, "1", false},
        new Object[] {SegmentConditions.GREATER_THAN, 0, "1", false},
        new Object[] {SegmentConditions.GREATER_THAN, 2.1, "2.0", true},
        new Object[] {SegmentConditions.GREATER_THAN, 2.1, "2.1", false},
        new Object[] {SegmentConditions.GREATER_THAN, 2.0, "2.1", false},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, 2, "1", true},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, 1, "1", true},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, 0, "1", false},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, 2.1, "2.0", true},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, 2.1, "2.1", true},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, 2.0, "2.1", false},
        new Object[] {SegmentConditions.LESS_THAN, 1, "2", true},
        new Object[] {SegmentConditions.LESS_THAN, 1, "1", false},
        new Object[] {SegmentConditions.LESS_THAN, 1, "0", false},
        new Object[] {SegmentConditions.LESS_THAN, 2.0, "2.1", true},
        new Object[] {SegmentConditions.LESS_THAN, 2.1, "2.1", false},
        new Object[] {SegmentConditions.LESS_THAN, 2.1, "2.0", false},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, 1, "2", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, 1, "1", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, 1, "0", false},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, 2.0, "2.1", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, 2.1, "2.1", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, 2.1, "2.0", false},
        new Object[] {SegmentConditions.NOT_EQUAL, "bar", "baz", true},
        new Object[] {SegmentConditions.NOT_EQUAL, "bar", "bar", false},
        new Object[] {SegmentConditions.NOT_EQUAL, 1, "2", true},
        new Object[] {SegmentConditions.NOT_EQUAL, 1, "1", false},
        new Object[] {SegmentConditions.NOT_EQUAL, true, "false", true},
        new Object[] {SegmentConditions.NOT_EQUAL, false, "true", true},
        new Object[] {SegmentConditions.NOT_EQUAL, false, "false", false},
        new Object[] {SegmentConditions.NOT_EQUAL, true, "true", false},
        new Object[] {SegmentConditions.CONTAINS, "bar", "b", true},
        new Object[] {SegmentConditions.CONTAINS, "bar", "bar", true},
        new Object[] {SegmentConditions.CONTAINS, "bar", "baz", false},
        new Object[] {SegmentConditions.NOT_CONTAINS, "bar", "b", false},
        new Object[] {SegmentConditions.NOT_CONTAINS, "bar", "bar", false},
        new Object[] {SegmentConditions.NOT_CONTAINS, "bar", "baz", true},
        new Object[] {SegmentConditions.REGEX, "foo", "[a-z]+", true},
        new Object[] {SegmentConditions.REGEX, "FOO", "[a-z]+", false},
        new Object[] {SegmentConditions.MODULO, 2, "2|0", true},
        new Object[] {SegmentConditions.MODULO, 3, "2|0", false},
        new Object[] {SegmentConditions.MODULO, 2.0, "2|0", true},
        new Object[] {SegmentConditions.MODULO, "foo", "2|0", false},
        new Object[] {SegmentConditions.MODULO, "foo", "foo|bar", false},
    };
  }

  @Test(dataProvider = "conditionTestData")
  public void testSegmentConditionMatchesTraitValue(
      SegmentConditions condition,
      Object traitValue,
      String conditionValue,
      Boolean expectedResponse) {

    SegmentConditionModel conditionModel = new SegmentConditionModel();
    conditionModel.setValue(conditionValue);
    conditionModel.setOperator(condition);
    conditionModel.setProperty_("foo");

    Boolean actualResult = SegmentEvaluator.traitsMatchValue(conditionModel, traitValue);

    Assert.assertTrue(actualResult.equals(expectedResponse));
  }

  @DataProvider(name = "semverTestData")
  public Object[][] semverTestData() {
    return new Object[][] {
        new Object[] {SegmentConditions.EQUAL, "1.0.0", "1.0.0:semver", true},
        new Object[] {SegmentConditions.EQUAL, "1.0.0", "1.0.1:semver", false},
        new Object[] {SegmentConditions.NOT_EQUAL, "1.0.0", "1.0.0:semver", false},
        new Object[] {SegmentConditions.NOT_EQUAL, "1.0.0", "1.0.1:semver", true},
        new Object[] {SegmentConditions.GREATER_THAN, "1.0.1", "1.0.0:semver", true},
        new Object[] {SegmentConditions.GREATER_THAN, "1.0.0", "1.0.0-beta:semver", true},
        new Object[] {SegmentConditions.GREATER_THAN, "1.0.1", "1.2.0:semver", false},
        new Object[] {SegmentConditions.GREATER_THAN, "1.0.1", "1.0.1:semver", false},
        new Object[] {SegmentConditions.GREATER_THAN, "1.2.4", "1.2.3-pre.2+build.4:semver", true},
        new Object[] {SegmentConditions.LESS_THAN, "1.0.0", "1.0.1:semver", true},
        new Object[] {SegmentConditions.LESS_THAN, "1.0.0", "1.0.0:semver", false},
        new Object[] {SegmentConditions.LESS_THAN, "1.0.1", "1.0.0:semver", false},
        new Object[] {SegmentConditions.LESS_THAN, "1.0.0-rc.2", "1.0.0-rc.3:semver", true},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, "1.0.1", "1.0.0:semver", true},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, "1.0.1", "1.2.0:semver", false},
        new Object[] {SegmentConditions.GREATER_THAN_INCLUSIVE, "1.0.1", "1.0.1:semver", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, "1.0.0", "1.0.1:semver", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, "1.0.0", "1.0.0:semver", true},
        new Object[] {SegmentConditions.LESS_THAN_INCLUSIVE, "1.0.1", "1.0.0:semver", false},
    };
  }

  @Test(dataProvider = "semverTestData")
  public void testSemverMatchesTraitValue(
      SegmentConditions condition,
      Object traitValue,
      String conditionValue,
      Boolean expectedResponse) {

    SegmentConditionModel conditionModel = new SegmentConditionModel();
    conditionModel.setValue(conditionValue);
    conditionModel.setOperator(condition);
    conditionModel.setProperty_("foo");

    Boolean actualResult = SegmentEvaluator.traitsMatchValue(conditionModel, traitValue);

    Assert.assertTrue(actualResult.equals(expectedResponse));
  }


}
