package com.checkout.payment.gateway.aspect;

/**
 * TODO: Simple metrics collector.
 * KISS: Just counters (AtomicLong) in memory. Expose via /metrics endpoint.
 * No need for Micrometer/Prometheus unless you actually have a Prometheus server.
 */
public interface Metrics {
  void increment(String name, String... tags);
  void recordTime(String name, long millis, String... tags);
}
