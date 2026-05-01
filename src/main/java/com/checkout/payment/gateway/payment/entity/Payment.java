package com.checkout.payment.gateway.payment.entity;

import com.checkout.payment.gateway.shared.enums.Currency;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Payment(
    UUID id,
    String cardNumber,
    Integer expiryMonth,
    Integer expiryYear,
    String cvv,
    Currency currency,
    Integer amount,
    UUID authorizationCode,
    PaymentStatus status,
    LocalDateTime createdAt) {

  public String getCardNumberLastFour() {
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
