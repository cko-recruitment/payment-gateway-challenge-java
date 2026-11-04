package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.LuhnCheck;
import java.io.Serializable;

public record PostPaymentRequest(
    @NotNull
    @Size(min = 14, max = 19)
    @Pattern(regexp = DIGITS_ONLY_REGEX)
    @LuhnCheck
    String cardNumber,
    @Min(1)
    @Max(12)
    @NotNull
    int expiryMonth,
    @NotNull
    int expiryYear,
    @NotNull
    @Size(min = 3, max = 3)
    String currency,
    @Positive
    @NotNull
    int amount,
    @NotNull
    @Size(min = 3, max = 4)
    @Pattern(regexp = DIGITS_ONLY_REGEX)
    String cvv
) implements Serializable {

  private static final String DIGITS_ONLY_REGEX = "\\d+";

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  public String getCardNumberLastFour() {
    return this.cardNumber.substring(this.cardNumber.length() - 4);
  }
}
