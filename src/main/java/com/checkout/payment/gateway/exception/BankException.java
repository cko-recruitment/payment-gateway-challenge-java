package com.checkout.payment.gateway.exception;

/**
 * A call to the acquiring bank failed, categorised using {@link BankFailure}
 */
public class BankException extends RuntimeException {

  private final transient BankFailure failure;

  public BankException(BankFailure failure, String message) {
    this(failure, message, null);
  }

  public BankException(BankFailure failure, String message, Throwable cause) {
    super(message, cause);
    this.failure = failure;
  }

  public BankFailure failure() {
    return failure;
  }
}
