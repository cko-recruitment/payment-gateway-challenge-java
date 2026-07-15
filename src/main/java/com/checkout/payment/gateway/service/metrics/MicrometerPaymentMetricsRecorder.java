package com.checkout.payment.gateway.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MicrometerPaymentMetricsRecorder implements PaymentMetricsRecorder {
  private final MeterRegistry meterRegistry;

  public MicrometerPaymentMetricsRecorder(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void incrementTotalRequests() {
    increment("payment.requests.total");
  }

  @Override
  public void incrementAuthorized() {
    increment("payment.requests.authorized");
  }

  @Override
  public void incrementDeclined() {
    increment("payment.requests.declined");
  }

  @Override
  public void incrementRejected() {
    increment("payment.requests.rejected");
  }

  @Override
  public void incrementIdempotencyReplay() {
    increment("payment.requests.idempotency_replay");
  }

  @Override
  public void incrementIdempotencyConflict() {
    increment("payment.requests.idempotency_conflict");
  }

  @Override
  public void incrementBankUnavailable() {
    increment("payment.requests.bank_unavailable");
  }

  private void increment(String metricName) {
    Counter.builder(metricName).register(meterRegistry).increment();
  }
}
