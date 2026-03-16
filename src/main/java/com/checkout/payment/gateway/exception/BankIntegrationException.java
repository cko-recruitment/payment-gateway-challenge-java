package com.checkout.payment.gateway.exception;

public class BankIntegrationException extends RuntimeException{
  public BankIntegrationException (String message) {
    super(message);
  }

  public BankIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
