package com.checkout.payment.gateway.acquirer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AcquirerPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String currency,
    Integer amount,
    String cvv
) {

  @Override
  public String toString() {
    return "AcquirerPaymentRequest{payment details redacted}";
  }
}
