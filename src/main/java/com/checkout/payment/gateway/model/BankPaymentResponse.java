package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BankPaymentResponse(
    // Boxed so that a missing field is null rather than defaulting to false, which would turn an
    // authorisation the bank did not report into a decline.
    @JsonProperty("authorized")
    Boolean authorized,
    @JsonProperty("authorization_code")
    String authorizationCode
) implements Serializable {

  public boolean isAuthorized() {
    return Boolean.TRUE.equals(authorized);
  }
}
