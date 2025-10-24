package com.flagsmith.models.features;

import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class MultivariateFeatureOptionModel extends BaseModel {
  private String value;
}