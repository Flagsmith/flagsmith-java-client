package com.flagsmith.interfaces;

import com.flagsmith.flagengine.environments.EnvironmentModel;

public interface IOfflineHandler {
  EnvironmentModel getEnvironment();
}
