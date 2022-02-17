package com.flagsmith.interfaces;

import com.flagsmith.models.DefaultFlag;

public interface DefaultFlagHandler {

  public DefaultFlag evaluateDefaultFlag(String featureName);

}
