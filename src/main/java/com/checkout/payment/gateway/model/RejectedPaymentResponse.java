package com.checkout.payment.gateway.model;

public class RejectedPaymentResponse {

  private String status;
  private String message;

  public RejectedPaymentResponse() {
  }

  public RejectedPaymentResponse(String status, String message) {
    this.status = status;
    this.message = message;
  }

  public String getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setMessage(String message) {
    this.message = message;
  }

}
