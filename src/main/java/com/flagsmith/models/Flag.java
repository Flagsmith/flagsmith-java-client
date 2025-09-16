package com.flagsmith.models;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class Flag extends BaseFlag {
  private Integer featureId = 0;
  private Boolean isDefault;

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

    return flag;
  }
}
