package com.flagsmith.interfaces;

import com.flagsmith.models.BaseFlag;
import java.util.function.Function;
import lombok.NonNull;

public interface DefaultFlagHandler {

  public void setDefaultFlagValueFunc(@NonNull Function<String, BaseFlag> defaultFlagValueFunc);

  public BaseFlag evaluateDefaultFlag(String featureName);

}
