package com.flagsmith.models.features;

import com.flagsmith.models.ExperimentMetadata;
import lombok.Data;

/** The {@code metadata} object of a remotely evaluated feature state. */
@Data
public class FeatureStateMetadata {
  private ExperimentMetadata experiment;
}
