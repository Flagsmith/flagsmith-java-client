package com.flagsmith.exceptions;

/*
 * Custom RuntimeException for use in the Flagsmith client code.
 *
 * TODO: this only extends RuntimeException to maintain backwards compatibility
 *  for any implementations that previously caught RuntimeException. We should
 *  consider changing this in a future major release since it's unnecessary.
 */
public class FlagsmithRuntimeError extends RuntimeException {

  public FlagsmithRuntimeError() {
    super();
  }

  public FlagsmithRuntimeError(String message) {
    super(message);
  }

  public FlagsmithRuntimeError(Throwable cause) {
    super(cause);
  }
}
