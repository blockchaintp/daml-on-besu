package com.blockchaintp.besu.daml.exceptions;

public class DamlBesuRuntimeException extends RuntimeException {

  public DamlBesuRuntimeException() {
    super();
  }

  public DamlBesuRuntimeException(String message) {
    super(message);
  }

  public DamlBesuRuntimeException(String message, Throwable t) {
    super(message, t);
  }

  public DamlBesuRuntimeException(Throwable t) {
    super(t);
  }

  protected DamlBesuRuntimeException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
