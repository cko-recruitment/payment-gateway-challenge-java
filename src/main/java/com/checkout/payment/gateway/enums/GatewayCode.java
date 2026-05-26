package com.checkout.payment.gateway.enums;

public enum GatewayCode {
  SUCCESS("SUCCESS", "Payment authorized successfully", "none"),
  DECLINED("PAYMENT_DECLINED", "Payment was declined by the bank", "contact_support"),
  INVALID_REQUEST("INVALID_REQUEST", "The request contains invalid data", "fix_request"),
  BANK_UNAVAILABLE("BANK_UNAVAILABLE", "The acquiring bank is temporarily unavailable", "retry"),
  BANK_VALIDATION_ERROR("BANK_VALIDATION_ERROR", "The acquiring bank returned a validation error", "fix_request"),
  BANK_ERROR("BANK_ERROR", "An error occurred while communicating with the bank", "retry"),
  INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected internal error occurred", "retry"),
  PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "The requested payment could not be found", "none");

  private final String code;
  private final String description;
  private final String action;

  GatewayCode(String code, String description, String action) {
    this.code = code;
    this.description = description;
    this.action = action;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public String getAction() {
    return action;
  }
}
