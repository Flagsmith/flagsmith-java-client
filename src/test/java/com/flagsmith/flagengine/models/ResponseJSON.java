package com.flagsmith.flagengine.models;

import com.flagsmith.models.FeatureStateModel;
import com.flagsmith.models.TraitModel;
import java.util.List;
import lombok.Data;

@Data
public class ResponseJSON {
  private List<FeatureStateModel> flags;
  private List<TraitModel> traits;
}
