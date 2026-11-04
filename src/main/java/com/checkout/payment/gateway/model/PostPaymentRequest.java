package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.ExpiringCard;
import com.checkout.payment.gateway.validation.ExpiryInFuture;
import com.checkout.payment.gateway.validation.SupportedCurrency;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

@ExpiryInFuture
public record PostPaymentRequest(
    @NotNull
    @Size(min = 14, max = 19)
    @Pattern(regexp = DIGITS_ONLY_REGEX)
    String cardNumber,
    @NotNull
    @Min(1)
    @Max(12)
    Integer expiryMonth,
    @NotNull
    @Min(value = 1000)
    @Max(value = 9999)
    Integer expiryYear,
    @NotBlank
    @SupportedCurrency
    String currency,
    @NotNull
    @Positive
    Integer amount,
    @NotNull
    @Size(min = 3, max = 4)
    @Pattern(regexp = DIGITS_ONLY_REGEX)
    String cvv
) implements Serializable, ExpiringCard {

  private static final String DIGITS_ONLY_REGEX = "[0-9]+";

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }
}
