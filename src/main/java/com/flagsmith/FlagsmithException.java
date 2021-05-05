package com.flagsmith;

public class FlagsmithException extends RuntimeException {

  public FlagsmithException(String message) {
    super(message);
  }

  public FlagsmithException(Throwable cause) {
    super(cause);
  }
}
