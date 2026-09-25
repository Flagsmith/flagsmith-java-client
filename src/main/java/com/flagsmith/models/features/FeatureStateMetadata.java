package com.flagsmith.models.features;

import com.flagsmith.models.ExperimentMetadata;
import lombok.Data;

/**
 * The {@code metadata} object returned alongside a remotely evaluated feature state. Keys other
 * than {@code experiment} are ignored.
 */
@Data
public class FeatureStateMetadata {
  private ExperimentMetadata experiment;
}
