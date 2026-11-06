package com.flagsmith.models;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FeatureMetadata
 * 
 * <p>Additional metadata associated with a feature.
 * 
 */
public class FeatureMetadata {
  private Integer flagsmithId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ExperimentMetadata experiment;

  /*
   * FlagsmithId
   * <p>The internal Flagsmith ID for the feature.
   * 
   * @return The flagsmithId
   */
  public Integer getFlagsmithId() {
    return flagsmithId;
  }

  /*
   * FlagsmithId
   * <p>The internal Flagsmith ID for the segment.
   * 
   * @param flagsmithId The flagsmithId
   */
  public void setFlagsmithId(Integer flagsmithId) {
    this.flagsmithId = flagsmithId;
  }

  /*
   * Experiment
   * <p>The running experiment the feature state belongs to, if any.
   *
   * @return The experiment
   */
  public ExperimentMetadata getExperiment() {
    return experiment;
  }

  public void setExperiment(ExperimentMetadata experiment) {
    this.experiment = experiment;
  }
}
