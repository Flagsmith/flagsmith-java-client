package com.flagsmith.flagengine.models;

import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import lombok.Data;

import java.util.List;

@Data
public class ResponseJSON {
  // TODO - this is not featureStates in essence,
  //  this is a response object

  private List<FeatureStateModel> flags;
  private List<TraitModel> traits;
}
