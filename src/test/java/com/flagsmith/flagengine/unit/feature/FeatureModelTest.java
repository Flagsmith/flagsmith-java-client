package com.flagsmith.flagengine.unit.feature;

import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.features.MultivariateFeatureOptionModel;
import com.flagsmith.flagengine.features.MultivariateFeatureStateValueModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

public class FeatureModelTest {

  private static String MV_FEATURE_CONTROL_VALUE = "control";
  private static String MV_FEATURE_VALUE_1 = "foo";
  private static String MV_FEATURE_VALUE_2 = "bar";

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

  private static Stream<Arguments> dataProviderForFeatureStateValueTest() {
    return Stream.of(
        Arguments.of(10f, MV_FEATURE_VALUE_1),
        Arguments.of(40f, MV_FEATURE_VALUE_2),
        Arguments.of(70f, MV_FEATURE_CONTROL_VALUE)
    );
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
    mv1.setValue(MV_FEATURE_VALUE_1);

    MultivariateFeatureOptionModel mv2 = new MultivariateFeatureOptionModel();
    mv2.setId(2);
    mv2.setValue(MV_FEATURE_VALUE_2);

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
    featureState.setValue(MV_FEATURE_CONTROL_VALUE);

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
