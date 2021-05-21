package com.blockchaintp.besu.daml.exceptions;

public class DamlBesuException extends Exception {

  public DamlBesuException() {
    super();
  }

  public DamlBesuException(String message) {
    super(message);
  }

  public DamlBesuException(String message, Throwable t) {
    super(message, t);
  }

  public DamlBesuException(Throwable t) {
    super(t);
  }

  protected DamlBesuException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
