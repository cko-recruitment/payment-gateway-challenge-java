package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Acquiring Bank Client Request Entity
 */
public class BankingClientRequest {

  @JsonProperty("card_number")
  private String cardNumber;
  @JsonProperty("expiry_date")
  private String expiryDate;
  private String cvv;
  private String currency;
  private Integer amount;

  public BankingClientRequest() {}
  public BankingClientRequest(String cardNumber, String expiryDate, String cvv, String currency,
      Integer amount) {
    this.cardNumber = cardNumber;
    this.expiryDate = expiryDate;
    this.cvv = cvv;
    this.currency = currency;
    this.amount = amount;
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public String getExpiryDate() {
    return expiryDate;
  }

  public void setExpiryDate(String expiryDate) {
    this.expiryDate = expiryDate;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
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

  @Override
  public String toString() {
    return "BankingClientRequest{" +
        "cardNumber='" + cardNumber + '\'' +
        ", expiryDate='" + expiryDate + '\'' +
        ", cvv='" + cvv + '\'' +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
