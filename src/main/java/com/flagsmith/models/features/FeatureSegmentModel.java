package com.flagsmith.models.features;

import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class FeatureSegmentModel extends BaseModel {
  private Integer priority;

  public FeatureSegmentModel() {
  }

  // Tolerates the integer foreign-key shape emitted by self-hosted Core/EE
  // /api/v1/identities/ — Edge emits {"priority": <int>} instead.
  public FeatureSegmentModel(Integer priority) {
    this.priority = priority;
  }
}