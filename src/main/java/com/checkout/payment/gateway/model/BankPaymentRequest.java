package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public class BankPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  private @NotNull
  @Size(min = 14, max = 19) String cardNumber;
  @JsonProperty("expiry_date")
  private String expiryDate;
  @NotNull
  @Size(min = 3, max = 3)
  private String currency;
  @NotNull
  private int amount;
  @NotNull
  @Size(min = 3, max = 4)
  private String cvv;

  public BankPaymentRequest(@NotNull @Size(min = 14, max = 19) String cardNumber, String expiryDate,
      String currency, int amount, String cvv) {
    this.cardNumber = cardNumber;
    this.expiryDate = expiryDate;
    this.currency = currency;
    this.amount = amount;
    this.cvv = cvv;
  }

  public @NotNull @Size(min = 14, max = 19) String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(@NotNull @Size(min = 14, max = 19) String cardNumberLastFour) {
    this.cardNumber = cardNumberLastFour;
  }

  public String getExpiryDate() {
    return expiryDate;
  }

  public void setExpiryDate(String expiryDate) {
    this.expiryDate = expiryDate;
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

  @Override
  public String toString() {
    return "BankPaymentRequest{" +
        "cardNumber=" + maskedCardNumber() +
        ", expiryDate=" + expiryDate +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=***" +
        '}';
  }

  private String maskedCardNumber() {
    if (cardNumber == null || cardNumber.length() < 4) {
      return "****";
    }
    return "****" + cardNumber.substring(cardNumber.length() - 4);
  }
}
