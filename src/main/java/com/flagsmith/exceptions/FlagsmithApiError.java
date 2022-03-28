package com.flagsmith.exceptions;

public class FlagsmithApiError extends FlagsmithClientError {

  public FlagsmithApiError() {
    super();
  }

  public FlagsmithApiError(String message) {
    super(message);
  }
}
