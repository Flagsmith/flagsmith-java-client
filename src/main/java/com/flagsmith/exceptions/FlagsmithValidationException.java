package com.flagsmith.exceptions;

public class FlagsmithValidationException extends FlagsmithClientError {

  public FlagsmithValidationException() {
    super();
  }

  public FlagsmithValidationException(String message) {
    super(message);
  }
}
