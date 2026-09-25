package com.flagsmith.flagengine.models;

import com.flagsmith.MapperFactory;
import com.flagsmith.models.ExperimentMetadata;
import com.flagsmith.models.Flag;
import com.flagsmith.models.features.FeatureStateModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlagTest {
  @Test
  public void testToString() {
    Flag flag = new Flag();
    flag.setEnabled(true);
    flag.setIsDefault(false);
    flag.setFeatureName("my_feature");
    flag.setValue("foo");
    flag.setFeatureId(1);

    // BaseModel has no toString(), so the "super=" part carries an identity hash. Only the
    // fields Lombok renders are asserted.
    String expected = ", enabled=true, "
        + "value=foo, "
        + "featureName=my_feature), "
        + "featureId=1, "
        + "isDefault=false, "
        + "variant=null, "
        + "reason=null, "
        + "experiment=null)";

    assertTrue(flag.toString().startsWith("Flag(super=BaseFlag(super="), flag.toString());
    assertTrue(flag.toString().endsWith(expected), flag.toString());
  }

  @Test
  public void fromFeatureStateModel_copiesExperimentMetadata() throws JsonProcessingException {
    FeatureStateModel featureState = MapperFactory.getMapper().readValue(
        "{\"feature\": {\"id\": 1, \"name\": \"checkout_cta\"},"
            + " \"enabled\": true,"
            + " \"feature_state_value\": \"buy-now\","
            + " \"variant\": \"treatment\","
            + " \"reason\": \"SPLIT; weight=70.0\","
            + " \"metadata\": {\"experiment\": {\"id\": 42, \"name\": \"exp\","
            + " \"in_experiment\": true}}}",
        FeatureStateModel.class);

    Flag flag = Flag.fromFeatureStateModel(featureState);

    assertEquals("treatment", flag.getVariant());
    assertEquals("SPLIT; weight=70.0", flag.getReason());

    ExperimentMetadata experiment = flag.getExperiment();
    assertEquals(42, experiment.getId());
    assertEquals("exp", experiment.getName());
    assertEquals(Boolean.TRUE, experiment.getInExperiment());
  }

  @Test
  public void fromFeatureStateModel_leavesExperimentMetadataNullWhenAbsent()
      throws JsonProcessingException {
    FeatureStateModel featureState = MapperFactory.getMapper().readValue(
        "{\"feature\": {\"id\": 1, \"name\": \"some_feature\"},"
            + " \"enabled\": true, \"feature_state_value\": \"some-value\"}",
        FeatureStateModel.class);

    Flag flag = Flag.fromFeatureStateModel(featureState);

    assertNull(flag.getVariant());
    assertNull(flag.getReason());
    assertNull(flag.getExperiment());
  }
}
