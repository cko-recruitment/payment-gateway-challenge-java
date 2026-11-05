package com.checkout.payment.gateway.exception;

import java.util.UUID;

/**
 * Thrown when no payment is held for the requested id.
 */
public class PaymentNotFoundException extends RuntimeException {

  private final UUID id;

  public PaymentNotFoundException(UUID id) {
    super("No payment found with ID " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
