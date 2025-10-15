package com.flagsmith.interfaces;

import com.flagsmith.models.environments.EnvironmentModel;

public interface IOfflineHandler {
  EnvironmentModel getEnvironment();
}
