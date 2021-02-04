package com.flagsmith;

import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;

import java.io.IOException;

public class FlagsmithLogger {
  private Logger logger;
  private FlagsmithLoggerLevel level = FlagsmithLoggerLevel.ERROR;

  public void setLogger(Logger logger, FlagsmithLoggerLevel level) {
    this.logger = logger;
    this.level = level;
  }

  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  public void httpError(Request request, Response response) {
    if (logger != null) {
      String body = null;
      try {
        body = response.body().string();
      } catch (IOException e) {
      }
      logger.error("Flagsmith: error when getting flags. Request: {}, Response: {} body[{}]", request.url(), response, body);
    }
  }

  public void httpError(Request request, IOException io) {
    if (logger != null) {
      logger.error("Flagsmith: error when getting flags. Request: {}", request.url(), io);
    }
  }

  public void info(String var1, Object... var2) {
    if (logger != null && FlagsmithLoggerLevel.INFO.getValue() >= level.getValue()) {
      logger.info("Flagsmith: " + var1, var2);
    }
  }
}
