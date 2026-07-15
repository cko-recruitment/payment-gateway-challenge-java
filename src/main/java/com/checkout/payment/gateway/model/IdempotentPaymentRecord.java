package com.checkout.payment.gateway.model;

public class IdempotentPaymentRecord {
  private final String requestSignature;
  private final PostPaymentResponse response;

  public IdempotentPaymentRecord(String requestSignature, PostPaymentResponse response) {
    this.requestSignature = requestSignature;
    this.response = response;
  }

  public String getRequestSignature() {
    return requestSignature;
  }

  public PostPaymentResponse getResponse() {
    return response;
  }
}
