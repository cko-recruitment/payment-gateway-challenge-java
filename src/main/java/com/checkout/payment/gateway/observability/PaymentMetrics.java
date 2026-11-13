package com.checkout.payment.gateway.observability;

import static com.checkout.payment.gateway.observability.ObservabilityConstants.AUTHORIZED;
import static com.checkout.payment.gateway.observability.ObservabilityConstants.DECLINED;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

  private final MeterRegistry registry;

  public PaymentMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordPayment(boolean authorized) {
    increment(authorized ? AUTHORIZED : DECLINED);
  }

  public void recordAcquirerFailure(boolean unavailable) {
    increment(unavailable ? "acquirer_unavailable" : "acquirer_error");
  }

  private void increment(String outcome) {
    registry.counter("payments.processed", "outcome", outcome).increment();
  }
}
