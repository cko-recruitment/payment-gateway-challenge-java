package com.checkout.payment.gateway.model;

public class RequestEvent {
  private String message;

  public RequestEvent(String message) {
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
    return "RequestEvent{" +
        "message='" + message + '\'' +
        '}';
  }
}
