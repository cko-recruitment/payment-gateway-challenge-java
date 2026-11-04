package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record PaymentData(UUID id,
                          boolean authorized,
                          String cardNumberLastFour,
                          int expiryMonth,
                          int expiryYear,
                          String currency,
                          int amount,
                          String authorizationCode) {


  public static PaymentData createPaymentData(boolean authorized, String cardNumberLastFour,
      int expiryMonth,
      int expiryYear, String currency, int amount, String authorizationCode) {
    UUID id = UUID.randomUUID();
    return new PaymentData(id, authorized, cardNumberLastFour, expiryMonth,
        expiryYear, currency, amount, authorizationCode);
  }
}




