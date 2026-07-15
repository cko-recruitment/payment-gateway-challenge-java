package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BankPaymentRequest {
  @JsonProperty("card_number")
  private final String cardNumber;
  @JsonProperty("expiry_date")
  private final String expiryDate;
  private final String currency;
  private final int amount;
  private final String cvv;

  public BankPaymentRequest(String cardNumber, String expiryDate, String currency, int amount, String cvv) {
    this.cardNumber = cardNumber;
    this.expiryDate = expiryDate;
    this.currency = currency;
    this.amount = amount;
    this.cvv = cvv;
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public String getExpiryDate() {
    return expiryDate;
  }

  public String getCurrency() {
    return currency;
  }

  public int getAmount() {
    return amount;
  }

  public String getCvv() {
    return cvv;
  }
}
