package com.flagsmith.flagengine.features;

import com.flagsmith.flagengine.utils.models.BaseModel;

import lombok.Data;

@Data
public class FeatureSegmentModel extends BaseModel {
  private Integer priority;

  public FeatureSegmentModel() {
    this.priority = 0;
  }

  public FeatureSegmentModel(Integer priority) {
    this.priority = priority;
  }
}
