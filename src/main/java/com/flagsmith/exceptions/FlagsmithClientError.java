package com.flagsmith.exceptions;

public class FlagsmithClientError extends Exception {

  public FlagsmithClientError() { super(); }

  public FlagsmithClientError(String message) { super(message); }
}
