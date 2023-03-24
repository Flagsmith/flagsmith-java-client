package com.flagsmith.exceptions;

public class FeatureNotFoundError extends FlagsmithClientError {

  public FeatureNotFoundError() {
    super();
  }

  public FeatureNotFoundError(String message) {
    super(message);
  }
}
