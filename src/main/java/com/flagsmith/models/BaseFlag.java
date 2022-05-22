package com.flagsmith.models;

import static com.flagsmith.flagengine.utils.types.TypeCasting.toBoolean;
import static com.flagsmith.flagengine.utils.types.TypeCasting.toFloat;
import static com.flagsmith.flagengine.utils.types.TypeCasting.toInteger;

import com.flagsmith.flagengine.features.FlagsmithValue;
import com.flagsmith.flagengine.features.FlagsmithValueType;
import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class BaseFlag extends BaseModel {
  private Boolean enabled;
  private FlagsmithValue value;
  private String featureName;

  public String getStringValue() {
    return this.value.getValueType() == FlagsmithValueType.STRING
        ? this.value.getValue() : null;
  }

  public Integer getIntegerValue() {
    return this.value.getValueType() == FlagsmithValueType.INTEGER
        ? toInteger(this.value.getValue()) : null;
  }

  public Float getFloatValue() {
    return this.value.getValueType() == FlagsmithValueType.FLOAT
        ? toFloat(this.value.getValue()) : null;
  }

  public Boolean getBooleanValue() {
    return this.value.getValueType() == FlagsmithValueType.BOOLEAN
        ? toBoolean(this.value.getValue()) : null;
  }
}
