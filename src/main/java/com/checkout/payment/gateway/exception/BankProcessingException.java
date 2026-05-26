package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public class BankProcessingException extends PaymentGatewayException {

  private static final String CODE = "BANK_PROCESSING_ERROR";

  private final org.springframework.http.HttpStatusCode bankStatusCode;

  public BankProcessingException(String message) {
    super(message, HttpStatus.BAD_GATEWAY, CODE);
    this.bankStatusCode = null;
  }

  public BankProcessingException(String message, Throwable cause) {
    super(message, cause, HttpStatus.BAD_GATEWAY, CODE);
    this.bankStatusCode = null;
  }

  public BankProcessingException(String message, org.springframework.http.HttpStatusCode bankStatusCode) {
    super(message, HttpStatus.BAD_GATEWAY, CODE);
    this.bankStatusCode = bankStatusCode;
  }

  public org.springframework.http.HttpStatusCode getBankStatusCode() {
    return bankStatusCode;
  }
}
