package com.checkout.payment.gateway.exception;

/**
 * Exception thrown when Payment(UUID paymentId) is not found in the repository
 */
public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(String message) {
    super(message);
  }

}
