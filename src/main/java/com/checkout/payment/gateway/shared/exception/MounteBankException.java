package com.checkout.payment.gateway.shared.exception;

public class MounteBankException extends RuntimeException {

  public MounteBankException(Exception ex) {
    super(ex);
  }
}