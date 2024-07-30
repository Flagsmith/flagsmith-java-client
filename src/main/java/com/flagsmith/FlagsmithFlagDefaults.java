package com.flagsmith;

import com.flagsmith.interfaces.DefaultFlagHandler;
import com.flagsmith.models.BaseFlag;
import java.util.function.Function;
import lombok.NonNull;

public class FlagsmithFlagDefaults implements DefaultFlagHandler {

  private Function<String, BaseFlag> defaultFlagValueFunc = (String flagName) -> null;

  /**
   * Set the evaluation function.
   *
   * @param defaultFlagValueFunc function to determine default flag
   */
  public void setDefaultFlagValueFunc(@NonNull Function<String, BaseFlag> defaultFlagValueFunc) {
    this.defaultFlagValueFunc = defaultFlagValueFunc;
  }

  /**
   * evaluate the default feature flag.
   *
   * @param flagName feature name
   */
  public BaseFlag evaluateDefaultFlag(String flagName) {
    return defaultFlagValueFunc.apply(flagName);
  }
}
