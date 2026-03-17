package com.checkout.payment.gateway.exception;

/**
 * Exception thrown when Acquiring Bank client returns 5xx errors
 */
public class BankUnavailableException extends RuntimeException {
  public BankUnavailableException (String message) {
     super(message);
  }

  public BankUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

}
