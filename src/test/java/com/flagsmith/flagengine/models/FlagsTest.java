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
        .withFeatureKey("feature_1")
        .withName("Feature 1")
        .withValue("value_1")
        .withReason("DEFAULT")
        .withMetadata(Map.of("flagsmithId", 1)))
      .withAdditionalProperty("feature_2", new FlagResult()
        .withEnabled(false)
        .withFeatureKey("feature_2")
        .withName("Feature 2")
        .withValue(null)
        .withReason("DEFAULT")
      );
    EvaluationResult evaluationResult = new EvaluationResult()
      .withFlags(flagResults);

    Flags flags = Flags.fromEvaluationResult(evaluationResult, null, null);
    Flag flag = (Flag) flags.getFlag("feature_1");

    assertEquals(1, flags.getFlags().size());
    assertEquals(true, flag.getEnabled());
    assertEquals("value_1", flag.getValue());
    assertEquals("Feature 1", flag.getFeatureName());
    assertEquals(1, flag.getFeatureId().intValue());
  }
}
