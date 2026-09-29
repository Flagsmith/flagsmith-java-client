package com.flagsmith.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Details of the experiment running on a feature, as returned by remote evaluation.
 */
@Data
public class ExperimentMetadata {
  private Integer id;
  private String name;
  @JsonProperty("in_experiment")
  private Boolean inExperiment = Boolean.FALSE;
}
