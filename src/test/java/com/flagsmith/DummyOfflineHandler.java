package com.flagsmith;

import com.flagsmith.interfaces.IOfflineHandler;
import com.flagsmith.models.environments.EnvironmentModel;

public class DummyOfflineHandler implements IOfflineHandler {
  public EnvironmentModel getEnvironment() {
    return FlagsmithTestHelper.environmentModel();
  }
}
