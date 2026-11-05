package com.checkout.payment.gateway.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public record PaymentRequestData(
    String cardNumber,
    int expiryMonth,
    int expiryYear,
    String currency,
    int amount,
    String cvv
) implements Serializable {

  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  public String getCardNumberLastFour() {
    return this.cardNumber.substring(this.cardNumber.length() - 4);
  }
}
