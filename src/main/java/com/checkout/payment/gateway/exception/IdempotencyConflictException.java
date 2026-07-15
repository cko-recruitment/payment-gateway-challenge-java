package com.checkout.payment.gateway.exception;

public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String message) {
    super(message);
  }
}
