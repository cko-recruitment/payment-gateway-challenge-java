package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Request to process a new payment")
public class PostPaymentRequest implements Serializable {

  @Schema(description = "Card number (14-19 digits)", example = "4242424242424242")
  @JsonProperty("card_number")
  private String cardNumber;

  @Schema(description = "Expiry month (1-12)", example = "12")
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @Schema(description = "Expiry year (4 digits)", example = "2027")
  @JsonProperty("expiry_year")
  private int expiryYear;

  @Schema(description = "ISO 4217 currency code", example = "USD")
  private String currency;

  @Schema(description = "Amount in minor units (cents)", example = "100")
  private int amount;

  @Schema(description = "Card verification value (3-4 digits)", example = "123")
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

  @Schema(hidden = true)
  public String getExpiryDate() {
    return String.format("%02d/%02d", expiryMonth, expiryYear % 100);
  }

  @Schema(hidden = true)
  public String getLastFourDigits() {
    if (cardNumber == null || cardNumber.length() < 4) {
      return "0000";
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber='[MASKED]'" +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv='[MASKED]'" +
        '}';
  }
}
