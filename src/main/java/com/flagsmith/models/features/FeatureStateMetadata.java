package com.flagsmith.models.features;

import com.flagsmith.models.ExperimentMetadata;
import lombok.Data;

@Data
public class FeatureStateMetadata {
  private ExperimentMetadata experiment;
}
