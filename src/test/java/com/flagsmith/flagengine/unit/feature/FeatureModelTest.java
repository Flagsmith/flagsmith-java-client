package com.flagsmith.flagengine.unit.feature;

import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.features.MultivariateFeatureOptionModel;
import com.flagsmith.flagengine.features.MultivariateFeatureStateValueModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;

public class FeatureModelTest {

  private String mvFeatureControlValue = "control";
  private String mvFeatureValue1 = "foo";
  private String mvFeatureValue2 = "bar";

  public void featureStateModelShouldNotHaveEmpty() {
    FeatureStateModel featureStateModel = new FeatureStateModel();
    featureStateModel.setDjangoId(1234);
    featureStateModel.setEnabled(true);

    Assertions.assertNotNull(featureStateModel.getFeaturestateUuid());
  }

  public void testInitializingMultivariateFeatureStateValueCreatesDefaultUuid() {

    MultivariateFeatureOptionModel mvfom = new MultivariateFeatureOptionModel();
    mvfom.setValue("value");

    MultivariateFeatureStateValueModel mvfsvm = new MultivariateFeatureStateValueModel();
    mvfsvm.setMultivariateFeatureOption(mvfom);
    mvfsvm.setId(1);
    mvfsvm.setPercentageAllocation(10f);

    Assertions.assertNotNull(mvfsvm.getMvFsValueUuid());
  }

  public void testFeatureStateGetValueNoMvValues() {
    FeatureModel feature1 = new FeatureModel();
    feature1.setId(1);
    feature1.setName("mv_feature");
    feature1.setType("STANDARD");

    FeatureStateModel featureState = new FeatureStateModel();
    featureState.setFeature(feature1);
    featureState.setDjangoId(1);
    featureState.setEnabled(true);
    featureState.setValue("foo");

    Assertions.assertTrue(featureState.getValue().equals("foo"));
    Assertions.assertTrue(featureState.getValue(1).equals("foo"));
  }

  public Object[][] dataProviderForFeatureStateValueTest() {
    return new Object[][] {
        new Object[] {10f, mvFeatureValue1},
        new Object[] {40f, mvFeatureValue2},
        new Object[] {70f, mvFeatureControlValue},
    };
  }

  @ParameterizedTest
  @MethodSource("dataProviderForFeatureStateValueTest")
  public void testFeatureStateGetValueMvValues(Float percentageValue, String expectedValue) {
    FeatureModel feature1 = new FeatureModel();
    feature1.setId(1);
    feature1.setName("mv_feature");
    feature1.setType("STANDARD");

    MultivariateFeatureOptionModel mv1 = new MultivariateFeatureOptionModel();
    mv1.setId(1);
    mv1.setValue(mvFeatureValue1);

    MultivariateFeatureOptionModel mv2 = new MultivariateFeatureOptionModel();
    mv2.setId(2);
    mv2.setValue(mvFeatureValue2);

    MultivariateFeatureStateValueModel mvf1 = new MultivariateFeatureStateValueModel();
    mvf1.setPercentageAllocation(30f);
    mvf1.setId(1);
    mvf1.setMultivariateFeatureOption(mv1);

    MultivariateFeatureStateValueModel mvf2 = new MultivariateFeatureStateValueModel();
    mvf2.setPercentageAllocation(30f);
    mvf2.setId(1);
    mvf2.setMultivariateFeatureOption(mv2);

    FeatureStateModel featureState = new FeatureStateModel();
    featureState.setDjangoId(1);
    featureState.setFeature(feature1);
    featureState.setEnabled(true);
    featureState.setMultivariateFeatureStateValues(Arrays.asList(mvf1, mvf2));
    featureState.setDjangoId(1);
    featureState.setValue(mvFeatureControlValue);

    Object value = featureState.getValue(1);
    // TODO mock hash method
  }

  public void loadMultiVariateFeatureOptionWithoutId() throws Exception {
    String json = "{\"value\": 1}";
    MultivariateFeatureOptionModel variate =
        MultivariateFeatureOptionModel.load(MapperFactory.getMapper().readTree(json),
            MultivariateFeatureOptionModel.class);
    Assertions.assertNull(variate.getId());
  }

  public void loadMultiVariateFeatureStateWithoutId() throws Exception {
    String json =
        "{ \"multivariate_feature_option\":{\"value\": 1},\"percentage_allocation\": 10 }";
    MultivariateFeatureStateValueModel variate =
        MultivariateFeatureStateValueModel.load(MapperFactory.getMapper().readTree(json),
            MultivariateFeatureStateValueModel.class);
    Assertions.assertNull(variate.getId());
    Assertions.assertEquals(variate.getPercentageAllocation(), 10f);
  }
}
