package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public abstract class PaymentGatewayException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  protected PaymentGatewayException(String message, HttpStatus status, String code) {
    super(message);
    this.status = status;
    this.code = code;
  }

  protected PaymentGatewayException(String message, Throwable cause, HttpStatus status, String code) {
    super(message, cause);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
