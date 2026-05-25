package com.checkout.payment.gateway.model;

public class ErrorResponse {
  private String code;
  private String message;

  public ErrorResponse() {
  }

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
