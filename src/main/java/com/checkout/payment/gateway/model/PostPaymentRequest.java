package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.model.utils.ExpiryDateValidator;
import com.checkout.payment.gateway.model.utils.ValidExpiryDate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

@SuppressWarnings("unused")
@GroupSequence({PostPaymentRequest.class, ExpiryDateValidator.class})
@ValidExpiryDate(groups = ExpiryDateValidator.class)
public class PostPaymentRequest implements Serializable {

  @NotNull
  @Size(min = 14, max = 19)
  @Pattern(regexp = "\\d+")
  @JsonProperty("card_number")
  private String cardNumber;
  @NotNull
  @Min(1)
  @Max(12)
  @JsonProperty("expiry_month")
  private int expiryMonth;
  @JsonProperty("expiry_year")
  private int expiryYear;
  @NotNull
  @Size(min = 3, max = 3)
  @Pattern(regexp = "GBP|EUR|USD")
  private String currency;
  @NotNull
  @Positive
  private int amount;
  @NotNull
  @Size(min = 3, max = 4)
  @Pattern(regexp = "\\d+")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  public String extractLastFour() {
    return cardNumber == null ?
        "":
        cardNumber.substring(cardNumber.length() - 4);
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour=" + extractLastFour() +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + cvv +
        '}';
  }
}
