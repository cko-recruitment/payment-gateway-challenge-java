package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public class PaymentValidationException extends PaymentGatewayException {

  private static final String CODE = "PAYMENT_REJECTED";

  public PaymentValidationException(String message) {
    super(message, HttpStatus.BAD_REQUEST, CODE);
  }
}
