package com.flagsmith;

import static java.text.MessageFormat.format;

import java.io.IOException;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;

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

  /**
   * Based on configuration prints error into log or throws an exception.
   *
   * @param request  a Request
   * @param response a Response
   * @param doThrow  a boolean whether to throw exception
   */
  public void httpError(Request request, Response response, boolean doThrow) {
    if (!isLoggingEnabled(FlagsmithLoggerLevel.ERROR) && !doThrow) {
      return;
    }

    String body = null;
    try {
      body = response.body().string();
    } catch (IOException e) {
      // no handling
    } // ok

    String errorMessage = format(
        "Flagsmith: error when getting flags. Request: {0}, Response: {1} body[{2}]",
        request.url(), response, body);

    if (doThrow) {
      throw new FlagsmithException(errorMessage);
    } else {
      logger.error(errorMessage);
    }
  }

  /**
   * Based on configuration prints error into log or throws an exception.
   *
   * @param request   a Request
   * @param io        an IOException
   * @param doReThrow a boolean whether to throw exception
   */
  public void httpError(Request request, IOException io, boolean doReThrow) {
    if (doReThrow) {
      throw new FlagsmithException(io);
    } else if (isLoggingEnabled(FlagsmithLoggerLevel.ERROR)) {
      logger.error("Flagsmith: error when getting flags. Request: {}", request.url(), io);
    }
  }

  /**
   * Prints error into the log.
   *
   * @param var1 a param to print
   * @param var2 a param to print
   */
  public void error(String var1, Object... var2) {
    if (isLoggingEnabled(FlagsmithLoggerLevel.ERROR)) {
      logger.error("Flagsmith: " + var1, var2);
    }
  }

  /**
   * Prints info into the log.
   *
   * @param var1 a param to print
   * @param var2 a param to print
   */
  public void info(String var1, Object... var2) {
    if (isLoggingEnabled(FlagsmithLoggerLevel.INFO)) {
      logger.info("Flagsmith: " + var1, var2);
    }
  }

  private boolean isLoggingEnabled(FlagsmithLoggerLevel loggerLevel) {
    return logger != null && loggerLevel.getValue() >= level.getValue();
  }
}
