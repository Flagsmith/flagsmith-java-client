package com.flagsmith;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.interfaces.IOfflineHandler;

public class DummyOfflineHandler implements IOfflineHandler {
    public EnvironmentModel getEnvironment() {
        return FlagsmithTestHelper.environmentModel();
    }
}
