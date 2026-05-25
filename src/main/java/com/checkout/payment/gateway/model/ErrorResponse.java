package com.checkout.payment.gateway.model;

public class ErrorResponse {
  private final String code;
  private final String message;

  public ErrorResponse(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ErrorResponse{" +
        "code='" + code + '\'' +
        ", message='" + message + '\'' +
        '}';
  }
}
