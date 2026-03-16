package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

public class PaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @NotBlank(message = "card_number is required")
  @Pattern(regexp = "\\d{14,19}", message = "cardNumber must be between 14 and 19 digits")
  private String cardNumber;

  @JsonProperty("expiry_month")
  @NotNull(message = "expiry_month is required")
  @Min(value = 1, message = "expiryMonth must be between 1 and 12")
  @Max(value = 12, message = "expiryMonth must be between 1 and 12")
  private Integer expiryMonth;
  @JsonProperty("expiry_year")
  @NotNull(message = "expiry_year is required")
  @Min(value = 2000, message = "expiryYear is invalid")
  private Integer expiryYear;
  @NotBlank(message = "currency is required")
  @Pattern(regexp = "^(USD|EUR|GBP)$", message = "currency must be one of USD, EUR, GBP")
  private String currency;
  @NotNull(message = "amount is required")
  @Positive(message = "amount must be greater than zero")
  private Integer amount;
  @NotBlank(message = "cvv is required")
  @Pattern(regexp = "\\d{3,4}", message = "cvv must be 3 or 4 digits")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }
  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber=" + cardNumber +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + cvv +
        '}';
  }
}
