package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public class BankProcessingException extends PaymentGatewayException {

  private static final String CODE = "BANK_PROCESSING_ERROR";

  public BankProcessingException(String message) {
    super(message, HttpStatus.BAD_GATEWAY, CODE);
  }

  public BankProcessingException(String message, Throwable cause) {
    super(message, cause, HttpStatus.BAD_GATEWAY, CODE);
  }
}
