package com.checkout.payment.gateway.exception;

/**
 * Exception thrown when the Acquiring Bank Client rejects the payment request
 */
public class BankIntegrationException extends RuntimeException{
  public BankIntegrationException (String message) {
    super(message);
  }

  public BankIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
