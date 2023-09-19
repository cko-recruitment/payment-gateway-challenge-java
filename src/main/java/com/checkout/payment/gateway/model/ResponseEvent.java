package com.checkout.payment.gateway.model;

public class ResponseEvent {
  private String message;

  public ResponseEvent(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "ResponseEvent{" +
        "message='" + message + '\'' +
        '}';
  }
}
