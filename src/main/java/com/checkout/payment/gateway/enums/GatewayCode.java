package com.checkout.payment.gateway.enums;

public enum GatewayCode {
  SUCCESS("SUCCESS", "Payment authorized successfully", false),
  DECLINED("PAYMENT_DECLINED", "Payment was declined by the bank", false),
  INVALID_REQUEST("INVALID_REQUEST", "The request contains invalid data", false),
  BANK_UNAVAILABLE("BANK_UNAVAILABLE", "The acquiring bank is temporarily unavailable", true),
  BANK_VALIDATION_ERROR("BANK_VALIDATION_ERROR", "The acquiring bank returned a validation error", false),
  BANK_ERROR("BANK_ERROR", "An error occurred while communicating with the bank", true),
  INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected internal error occurred", true),
  PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "The requested payment could not be found", false);

  private final String code;
  private final String description;
  private final boolean retryable;

  GatewayCode(String code, String description, boolean retryable) {
    this.code = code;
    this.description = description;
    this.retryable = retryable;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
