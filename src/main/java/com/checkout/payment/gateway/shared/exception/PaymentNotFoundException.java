package com.checkout.payment.gateway.shared.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(UUID id) {
    super("Payment not found for id: %s".formatted(id));
  }
}