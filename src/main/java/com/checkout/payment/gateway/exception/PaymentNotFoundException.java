package com.checkout.payment.gateway.exception;

public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(String message) {
    super(message);
  }

}
