package com.blockchaintp.besu.daml.exceptions;

public class RecoverableException extends DamlBesuException {

  public RecoverableException(String message, Throwable t) {
    super(message, t);
  }

}
