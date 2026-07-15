package com.checkout.payment.gateway.service.metrics;

public interface PaymentMetricsRecorder {
  void incrementTotalRequests();

  void incrementAuthorized();

  void incrementDeclined();

  void incrementRejected();

  void incrementIdempotencyReplay();

  void incrementIdempotencyConflict();

  void incrementBankUnavailable();
}
