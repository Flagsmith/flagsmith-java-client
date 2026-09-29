package com.flagsmith.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.models.features.FeatureStateMetadata;
import com.flagsmith.models.features.FeatureStateModel;
import lombok.Data;

@Data
public class Flag extends BaseFlag {
  private Integer featureId = 0;
  private Boolean isDefault;
  /** Set by remote evaluation only. */
  private String variant;
  private String reason;
  /** Set by remote identity evaluation only. */
  private ExperimentMetadata experiment;

  /**
   * return flag from feature state model and identity id.
   *
   * @param featureState feature state model
   */
  public static Flag fromFeatureStateModel(FeatureStateModel featureState) {
    Flag flag = new Flag();

    flag.setFeatureId(featureState.getFeature().getId());
    flag.setValue(featureState.getValue());
    flag.setFeatureName(featureState.getFeature().getName());
    flag.setEnabled(featureState.getEnabled());
    flag.setVariant(featureState.getVariant());
    flag.setReason(featureState.getReason());
    flag.setExperiment(featureState.getMetadata() != null
        ? featureState.getMetadata().getExperiment() : null);

    return flag;
  }

  /**
   * Flag from api.
   *
   * @param node node object
   */
  public static Flag fromApiFlag(JsonNode node) {
    Flag flag = new Flag();

    flag.setFeatureId(node.get("feature").get("id").intValue());
    flag.setValue(node.get("feature_state_value"));
    flag.setFeatureName(node.get("feature").get("name").asText());
    flag.setEnabled(node.get("enabled").booleanValue());

    JsonNode variant = node.get("variant");
    if (variant != null && !variant.isNull()) {
      flag.setVariant(variant.asText());
    }

    JsonNode reason = node.get("reason");
    if (reason != null && !reason.isNull()) {
      flag.setReason(reason.asText());
    }

    JsonNode metadata = node.get("metadata");
    if (metadata != null && !metadata.isNull()) {
      flag.setExperiment(MapperFactory.getMapper()
          .convertValue(metadata, FeatureStateMetadata.class)
          .getExperiment());
    }

    return flag;
  }
}
