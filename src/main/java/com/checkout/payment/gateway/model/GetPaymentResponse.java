package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.GatewayCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for payment retrieval via GET endpoints")
public class GetPaymentResponse extends PostPaymentResponse {

  public static GetPaymentResponse from(PostPaymentResponse postResponse) {
    GetPaymentResponse getResponse = new GetPaymentResponse();
    getResponse.setId(postResponse.getId());
    getResponse.setStatus(postResponse.getStatus());
    getResponse.setCurrency(postResponse.getCurrency());
    getResponse.setAmount(postResponse.getAmount());
    getResponse.setCode(postResponse.getCode());
    getResponse.setDescription(postResponse.getDescription());
    getResponse.setAction(postResponse.getAction());
    getResponse.setLinks(postResponse.getLinks());
    return getResponse;
  }

  public static GetPaymentResponse notFound() {
    GetPaymentResponse response = new GetPaymentResponse();
    response.setGatewayCode(GatewayCode.PAYMENT_NOT_FOUND);
    return response;
  }
}
