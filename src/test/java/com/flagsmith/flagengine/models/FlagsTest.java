package com.flagsmith.flagengine.models;

import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.EvaluationResult;
import com.flagsmith.models.Flags;
import com.flagsmith.models.Flag;
import com.flagsmith.flagengine.FlagResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.Map;

public class FlagsTest {
  @Test
  public void testFromEvaluationResult__metadata__expected() throws FlagsmithClientError {
    com.flagsmith.flagengine.Flags flagResults = new com.flagsmith.flagengine.Flags()
      .withAdditionalProperty("feature_1", new FlagResult()
        .withEnabled(true)
        .withName("Feature 1")
        .withValue("value_1")
        .withReason("DEFAULT")
        .withMetadata(Map.of("flagsmithId", 1)))
      .withAdditionalProperty("feature_2", new FlagResult()
        .withEnabled(false)
        .withName("Feature 2")
        .withValue(null)
        .withReason("DEFAULT")
        .withMetadata(Map.of())
      );
    EvaluationResult evaluationResult = new EvaluationResult()
      .withFlags(flagResults);

    Flags flags = Flags.fromEvaluationResult(evaluationResult, null, null);
    
    assertEquals(2, flags.getFlags().size());
    assertEquals(1, ((Flag) flags.getFlag("feature_1")).getFeatureId().intValue());
    assertEquals(null, ((Flag) flags.getFlag("feature_2")).getFeatureId());
  }
}
