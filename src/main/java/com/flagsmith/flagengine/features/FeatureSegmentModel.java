package com.flagsmith.flagengine.features;

import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

@Data
public class FeatureSegmentModel extends BaseModel {
  private Integer priority;
}
