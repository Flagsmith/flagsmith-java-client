package com.flagsmith.flagengine.utils.serialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.flagsmith.flagengine.features.FlagsmithValue;
import java.io.IOException;

public class FlagsmithValueDeserializer extends StdDeserializer<FlagsmithValue> {

  public FlagsmithValueDeserializer() {
    this(null);
  }

  public FlagsmithValueDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public FlagsmithValue deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    JsonNode node = p.getCodec().readTree(p);

    return FlagsmithValue.fromUntypedValue(node.toString());
  }
}
