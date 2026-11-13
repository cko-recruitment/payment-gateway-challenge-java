package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.YearMonth;
import java.time.ZoneOffset;

public record PostPaymentRequest(
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{14,19}$", message = "Card number must be between 14 and 19 numeric digits")
    @JsonProperty("card_number")
    String cardNumber,

    @NotNull(message = "Expiry month is required")
    @Min(value = 1, message = "Expiry month must be between 1 and 12")
    @Max(value = 12, message = "Expiry month must be between 1 and 12")
    @JsonProperty("expiry_month")
    Integer expiryMonth,

    @NotNull(message = "Expiry year is required")
    @JsonProperty("expiry_year")
    Integer expiryYear,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^(INR|GBP|EUR)$", message = "Currency must be INR, GBP or EUR")
    String currency,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be in positive minor currency units")
    Integer amount,

    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 numeric digits")
    String cvv
) {

  @JsonIgnore
  @AssertTrue(message = "Card expiry date must be in the future")
  public boolean isExpiryDateInFuture() {
    if (expiryMonth == null || expiryYear == null || expiryMonth < 1 || expiryMonth > 12) {
      return true; // Required-field and month constraints handle these cases.
    }
    if (expiryYear < 1 || expiryYear > 9999) {
      return false;
    }
    return YearMonth.of(expiryYear, expiryMonth).isAfter(YearMonth.now(ZoneOffset.UTC));
  }

  public String cardNumberLastFour() {
    if (cardNumber == null || cardNumber.length() < 4) {
      return null;
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }

  public String expiryDate() {
    if (expiryMonth == null || expiryYear == null) {
      return null;
    }
    return (expiryMonth < 10 ? "0" : "") + expiryMonth + "/" + expiryYear;
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour='" + cardNumberLastFour() + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
