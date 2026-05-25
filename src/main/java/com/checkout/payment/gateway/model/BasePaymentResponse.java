package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.GatewayCode;

public class BasePaymentResponse {
  private String code;
  private String message;
  private boolean isRetryable;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public void setErrorMessage(String errorMessage) {
    this.message = errorMessage;
  }

  public String getErrorMessage() {
    return message;
  }

  public boolean isRetryable() {
    return isRetryable;
  }

  public void setRetryable(boolean retryable) {
    isRetryable = retryable;
  }

  public void setGatewayCode(GatewayCode gatewayCode) {
    this.code = gatewayCode.getCode();
    if (this.message == null || this.message.isBlank()) {
      this.message = gatewayCode.getDescription();
    }
    this.isRetryable = gatewayCode.isRetryable();
  }
}
