package com.flagsmith.flagengine.features;

import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class MultivariateFeatureOptionModel extends BaseModel {
  private FlagsmithValue value;

  private Integer id;

  /**
   * Set the value.
   * @param value untype object value.
   */
  public void setValue(Object value) {
    this.value = FlagsmithValue.fromUntypedValue(value);
  }
}
