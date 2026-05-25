package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends PaymentGatewayException {

  private static final String CODE = "PAYMENT_NOT_FOUND";

  public PaymentNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND, CODE);
  }
}
