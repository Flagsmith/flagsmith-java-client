package com.flagsmith.models.features;

import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class FeatureSegmentModel extends BaseModel {
  private Integer priority;
}