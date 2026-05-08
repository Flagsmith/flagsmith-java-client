package com.flagsmith.flagengine.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flagsmith.MapperFactory;
import com.flagsmith.models.features.FeatureStateModel;
import org.junit.jupiter.api.Test;

public class FeatureSegmentModelTest {

  @Test
  public void deserializesFeatureSegmentAsIntegerForeignKey() throws JsonProcessingException {
    String json = "{"
        + "\"feature\": {\"id\": 1, \"name\": \"my_feature\", \"type\": \"STANDARD\"},"
        + "\"enabled\": true,"
        + "\"feature_state_value\": \"foo\","
        + "\"feature_segment\": 321"
        + "}";

    FeatureStateModel model =
        MapperFactory.getMapper().readValue(json, FeatureStateModel.class);

    assertThat(model.getFeatureSegment()).isNotNull();
    assertThat(model.getFeatureSegment().getPriority()).isEqualTo(321);
  }

  @Test
  public void deserializesFeatureSegmentAsEngineObject() throws JsonProcessingException {
    String json = "{"
        + "\"feature\": {\"id\": 1, \"name\": \"my_feature\", \"type\": \"STANDARD\"},"
        + "\"enabled\": true,"
        + "\"feature_state_value\": \"foo\","
        + "\"feature_segment\": {\"priority\": 5}"
        + "}";

    FeatureStateModel model =
        MapperFactory.getMapper().readValue(json, FeatureStateModel.class);

    assertThat(model.getFeatureSegment()).isNotNull();
    assertThat(model.getFeatureSegment().getPriority()).isEqualTo(5);
  }

  @Test
  public void deserializesNullFeatureSegment() throws JsonProcessingException {
    String json = "{"
        + "\"feature\": {\"id\": 1, \"name\": \"my_feature\", \"type\": \"STANDARD\"},"
        + "\"enabled\": true,"
        + "\"feature_state_value\": \"foo\","
        + "\"feature_segment\": null"
        + "}";

    FeatureStateModel model =
        MapperFactory.getMapper().readValue(json, FeatureStateModel.class);

    assertThat(model.getFeatureSegment()).isNull();
  }
}
