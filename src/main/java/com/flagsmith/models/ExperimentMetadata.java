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
  /**
   * Whether this identity is enrolled in the experiment. The variant alone cannot tell: an
   * identity outside the rollout is still bucketed into a variant.
   */
  @JsonProperty("in_experiment")
  private Boolean inExperiment = Boolean.FALSE;
}
