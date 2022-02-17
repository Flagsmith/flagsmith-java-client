package com.flagsmith.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.flagengine.features.FeatureStateModel;
import lombok.Data;

@Data
public class Flag extends BaseFlag {
  private Integer featureId;
  private Boolean isDefault;

  public static Flag fromFeatureStateModel(FeatureStateModel featureState, Object identityId) {
    Flag flag = new Flag();

    flag.setFeatureId(featureState.getFeature().getId());
    flag.setValue(featureState.getValue(identityId));
    flag.setFeatureName(featureState.getFeature().getName());
    flag.setEnabled(featureState.getEnabled());

    return flag;
  }

  public static Flag fromApiFlag(JsonNode node) {
    Flag flag = new Flag();

    flag.setFeatureId(node.get("feature").get("id").intValue());
    flag.setValue(node.get("feature_state_value"));
    flag.setFeatureName(node.get("feature").get("id").toString());
    flag.setEnabled(node.get("enabled").booleanValue());

    return flag;
  }

  public static Flag fromApiFlag(FeatureStateModel node) {
    Flag flag = new Flag();

    flag.setFeatureId(node.getFeature().getId());
    flag.setValue(node.getValue());
    flag.setFeatureName(node.getFeature().getName());
    flag.setEnabled(node.getEnabled());

    return flag;
  }

  public static Flag fromApiFlag(com.flagsmith.Flag node) {
    Flag flag = new Flag();

    flag.setValue(node.getStateValue());
    flag.setFeatureName(node.getFeature().getName());
    flag.setEnabled(node.isEnabled());

    return flag;
  }
}
