package com.flagsmith.models;

import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class BaseFlag extends BaseModel {
  private Boolean enabled;
  private Object value;
  private String featureName;
}
