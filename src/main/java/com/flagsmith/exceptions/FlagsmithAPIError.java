package com.flagsmith.exceptions;

public class FlagsmithAPIError extends FlagsmithClientError {

  public FlagsmithAPIError() { super(); }

  public FlagsmithAPIError(String message) { super(message); }
}
