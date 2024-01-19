package com.flagsmith.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.MapperFactory;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.interfaces.IOfflineHandler;

import java.io.File;
import java.io.IOException;

public class LocalFileHandler implements IOfflineHandler {
  private EnvironmentModel environmentModel;
  private ObjectMapper objectMapper = MapperFactory.getMapper();

  /**
   * Instantiate a LocalFileHandler for use as an OfflineHandler.
   *
   * @param filePath - path to a json file containing the environment data.
   * @throws FlagsmithClientError
   *
   */
  public LocalFileHandler(String filePath) throws FlagsmithClientError {
    File file = new File(filePath);
    try {
      environmentModel = objectMapper.readValue(file, EnvironmentModel.class);
    } catch (IOException e) {
      throw new FlagsmithClientError("Unable to read environment from file " + filePath);
    }
  }

  public EnvironmentModel getEnvironment() {
    return environmentModel;
  }
}
