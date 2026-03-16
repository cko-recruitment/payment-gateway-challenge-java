package com.checkout.payment.gateway.exception;

public class PaymentRejectedException extends RuntimeException {

  public PaymentRejectedException (String message) {
    super (message);
  }

}
