package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public record PostPaymentRequest(
    @JsonProperty("card_number")
    @NotNull
    @Size(min = 14, max = 19)
    String cardNumber,
    @Min(1)
    @Max(12)
    @NotNull
    @JsonProperty("expiry_month")
    int expiryMonth,
    @NotNull
    @JsonProperty("expiry_year")
    int expiryYear,
    @NotNull
    @Size(min = 3, max = 3)
    String currency,
    @NotNull
    int amount,
    @NotNull
    @Size(min = 3, max = 4)
    String cvv
) implements Serializable {

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  public String getCardNumberLastFour() {
    return this.cardNumber.substring(this.cardNumber.length() - 4);
  }
}
