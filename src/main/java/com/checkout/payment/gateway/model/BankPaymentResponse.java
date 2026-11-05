package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BankPaymentResponse(
    Boolean authorized,
    String authorizationCode
) implements Serializable {

  public boolean isAuthorized() {
    return Boolean.TRUE.equals(authorized);
  }
}
