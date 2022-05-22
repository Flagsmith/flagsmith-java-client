package com.flagsmith.flagengine.utils.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.flagsmith.flagengine.features.FlagsmithValue;
import com.flagsmith.flagengine.features.FlagsmithValueType;
import com.flagsmith.flagengine.utils.types.TypeCasting;
import java.io.IOException;

public class FlagsmithValueSerializer extends StdSerializer<FlagsmithValue> {
  public FlagsmithValueSerializer() {
    this(null);
  }

  public FlagsmithValueSerializer(Class<FlagsmithValue> t) {
    super(t);
  }

  @Override
  public void serialize(
      FlagsmithValue value, JsonGenerator jgen, SerializerProvider provider)
      throws IOException, JsonProcessingException {

    if (value.getValueType().equals(FlagsmithValueType.BOOLEAN)) {
      jgen.writeBoolean(TypeCasting.toBoolean(value.getValue()));
    } else if (value.getValueType().equals(FlagsmithValueType.STRING)) {
      jgen.writeString(value.getValue());
    } else if (value.getValueType().equals(FlagsmithValueType.FLOAT)) {
      jgen.writeNumber(TypeCasting.toFloat(value.getValue()));
    } else if (value.getValueType().equals(FlagsmithValueType.INTEGER)) {
      jgen.writeNumber(TypeCasting.toInteger(value.getValue()));
    }
  }
}
