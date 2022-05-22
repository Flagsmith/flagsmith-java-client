package com.flagsmith.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.features.FlagsmithValue;
import lombok.Data;

@Data
public class Flag extends BaseFlag {
  private Integer featureId = 0;
  private Boolean isDefault;

  /**
   * return flag from feature state model and identity id.
   *
   * @param featureState feature state model
   * @param identityId identity id
   * @return
   */
  public static Flag fromFeatureStateModel(FeatureStateModel featureState, Object identityId) {
    Flag flag = new Flag();

    flag.setFeatureId(featureState.getFeature().getId());
    flag.setValue(featureState.getValue(identityId));
    flag.setFeatureName(featureState.getFeature().getName());
    flag.setEnabled(featureState.getEnabled());

    return flag;
  }

  /**
   * Flag from api.
   *
   * @param node node object
   * @return
   */
  public static Flag fromApiFlag(JsonNode node) {
    Flag flag = new Flag();

    flag.setFeatureId(node.get("feature").get("id").intValue());
    flag.setValue(FlagsmithValue.fromUntypedValue(node.get("feature_state_value").asText()));
    flag.setFeatureName(node.get("feature").get("name").asText());
    flag.setEnabled(node.get("enabled").booleanValue());

    return flag;
  }
}
