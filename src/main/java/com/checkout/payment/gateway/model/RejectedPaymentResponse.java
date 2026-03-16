package com.checkout.payment.gateway.model;

import java.util.List;

public class RejectedPaymentResponse {

  private String status;
  private List<String> messages;

  public RejectedPaymentResponse() {
  }

  public RejectedPaymentResponse(String status, List<String> messages) {
    this.status = status;
    this.messages = messages;
  }

  public String getStatus() {
    return status;
  }

  public List<String> getMessages() {
    return messages;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setMessage(List<String> messages) {
    this.messages = messages;
  }

}
