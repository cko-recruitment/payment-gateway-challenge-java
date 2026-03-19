package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.util.CardUtils;
import com.checkout.payment.gateway.validation.ValidExpiryDate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@ValidExpiryDate
public record PostPaymentRequest(
    @NotBlank
    @Size(min = 14, max = 19, message = "Card number must have 14-19 numeric values")
    @Pattern(regexp = "^[0-9]*$", message = "Card number must contain only digits")
    @JsonProperty("card_number")
    String cardNumber,

    @Min(value = 1, message = "Expiry month must be at least 1")
    @Max(value = 12, message = "Expiry month must be at most 12")
    @JsonProperty("expiry_month")
    int expiryMonth,

    @Min(value = 2024, message = "Expiry year must be valid and in the future")
    @JsonProperty("expiry_year")
    int expiryYear,

    @NotNull
    PaymentCurrency currency,

    @Positive
    long amount,

    @NotBlank
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    String cvv
){

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
            "cardNumberLastFourDigits='" + CardUtils.getLastFourCardDigits(cardNumber) + '\'' +
            ", expiryMonth=" + expiryMonth +
            ", expiryYear=" + expiryYear +
            ", currency=" + currency +
            ", amount=" + amount +
            ", cvv=***" +
            '}';
  }

}
