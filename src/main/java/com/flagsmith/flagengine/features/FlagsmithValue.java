package com.flagsmith.flagengine.features;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flagsmith.flagengine.utils.serialize.FlagsmithValueSerializer;
import com.flagsmith.flagengine.utils.types.TypeCasting;
import lombok.Data;

@Data
@JsonSerialize(using = FlagsmithValueSerializer.class)
public class FlagsmithValue {
  private String value;
  private FlagsmithValueType valueType;

  /**
   * Build from untyped value.
   * @param untypedValue untyped value.
   * @return
   */
  public static FlagsmithValue fromUntypedValue(Object untypedValue) {
    FlagsmithValue typedValue = new FlagsmithValue();

    if (untypedValue == null || untypedValue.toString().equalsIgnoreCase("null")) {
      typedValue.setValueType(FlagsmithValueType.NULL);
    } else if (TypeCasting.isInteger(untypedValue)) {
      typedValue.setValueType(FlagsmithValueType.INTEGER);
    } else if (TypeCasting.isFloat(untypedValue)) {
      typedValue.setValueType(FlagsmithValueType.FLOAT);
    } else if (TypeCasting.isBoolean(untypedValue)) {
      typedValue.setValueType(FlagsmithValueType.BOOLEAN);
    } else {
      typedValue.setValueType(FlagsmithValueType.STRING);
    }

    typedValue.setValue(untypedValue == null ? "null" : untypedValue.toString());

    return typedValue;
  }
}
