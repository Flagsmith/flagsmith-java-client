package com.flagsmith.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flagsmith.MapperFactory;
import com.flagsmith.models.features.FeatureStateModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FeatureStateModelTest {

  private static FeatureStateModel parse(String json) throws JsonProcessingException {
    return MapperFactory.getMapper().readValue(json, FeatureStateModel.class);
  }

  @Test
  public void parsesVariantReasonAndExperiment() throws JsonProcessingException {
    FeatureStateModel featureState = parse(
        "{\"feature\": {\"id\": 220175, \"name\": \"checkout_cta\", \"type\": \"MULTIVARIATE\"},"
            + " \"enabled\": true,"
            + " \"feature_state_value\": \"buy-now\","
            + " \"variant\": \"treatment\","
            + " \"reason\": \"SPLIT; weight=70.0\","
            + " \"metadata\": {\"experiment\": {\"id\": 167, \"name\": \"flutter_demo_exp\","
            + " \"in_experiment\": true}}}");

    assertEquals("treatment", featureState.getVariant());
    assertEquals("SPLIT; weight=70.0", featureState.getReason());

    ExperimentMetadata experiment = featureState.getMetadata().getExperiment();
    assertEquals(167, experiment.getId());
    assertEquals("flutter_demo_exp", experiment.getName());
    assertEquals(Boolean.TRUE, experiment.getInExperiment());
  }

  @Test
  public void ignoresUnknownMetadataKeys() throws JsonProcessingException {
    FeatureStateModel featureState = parse(
        "{\"feature\": {\"id\": 1, \"name\": \"checkout_cta\"},"
            + " \"enabled\": true,"
            + " \"metadata\": {\"something_else\": {\"a\": 1}, \"experiment\": {\"id\": 3,"
            + " \"in_experiment\": true}}}");

    assertNotNull(featureState.getMetadata());
    assertEquals(3, featureState.getMetadata().getExperiment().getId());
  }

  @Test
  public void inExperimentDefaultsToFalseWhenMissing() throws JsonProcessingException {
    FeatureStateModel featureState = parse(
        "{\"feature\": {\"id\": 1, \"name\": \"checkout_cta\"},"
            + " \"enabled\": true,"
            + " \"metadata\": {\"experiment\": {\"id\": 3, \"name\": \"exp\"}}}");

    assertEquals(Boolean.FALSE, featureState.getMetadata().getExperiment().getInExperiment());
  }

  @Test
  public void parsesPayloadWithoutAnyExperimentFields() throws JsonProcessingException {
    FeatureStateModel featureState = parse(
        "{\"feature\": {\"id\": 1, \"name\": \"some_feature\"},"
            + " \"enabled\": true, \"feature_state_value\": \"some-value\"}");

    assertNull(featureState.getVariant());
    assertNull(featureState.getReason());
    assertNull(featureState.getMetadata());
    assertEquals("some-value", featureState.getValue());
  }
}
