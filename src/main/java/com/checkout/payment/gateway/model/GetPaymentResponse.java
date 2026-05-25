package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.GatewayCode;

public class GetPaymentResponse extends PostPaymentResponse {
  
  public static GetPaymentResponse from(PostPaymentResponse postResponse) {
    GetPaymentResponse getResponse = new GetPaymentResponse();
    getResponse.setId(postResponse.getId());
    getResponse.setStatus(postResponse.getStatus());
    getResponse.setCardNumberLastFour(postResponse.getCardNumberLastFour());
    getResponse.setExpiryMonth(postResponse.getExpiryMonth());
    getResponse.setExpiryYear(postResponse.getExpiryYear());
    getResponse.setCurrency(postResponse.getCurrency());
    getResponse.setAmount(postResponse.getAmount());
    getResponse.setCode(postResponse.getCode());
    getResponse.setMessage(postResponse.getMessage());
    getResponse.setRetryable(postResponse.isRetryable());
    return getResponse;
  }

  public static GetPaymentResponse notFound() {
    GetPaymentResponse response = new GetPaymentResponse();
    response.setGatewayCode(GatewayCode.PAYMENT_NOT_FOUND);
    return response;
  }
}
