package com.checkout.payment.gateway.aspect;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Payment metrics via Micrometer + Prometheus.
 * Records business-level latency and outcomes for Grafana visualization.
 */
@Aspect
@Component
public class PaymentMetricsAspect {

  private final MeterRegistry registry;

  public PaymentMetricsAspect(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Around payment processing: timer by status (AUTHORIZED, DECLINED, REJECTED).
   */
  @Around("execution(* com.checkout.payment.gateway.service.PaymentGatewayService.processPayment(..))")
  public Object aroundProcessPayment(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.nanoTime();
    Object result = joinPoint.proceed();
    long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    if (registry != null && result instanceof PostPaymentResponse response) {
      String status = response.getStatus() != null ? response.getStatus().name() : "UNKNOWN";
      Timer.builder("payment.latency")
          .tag("status", status)
          .description("Payment processing latency")
          .publishPercentileHistogram()
          .register(registry)
          .record(latencyMs, TimeUnit.MILLISECONDS);

      registry.counter("payment.total", "status", status).increment();
    }
    return result;
  }

  /**
   * Around bank client: timer for external bank call.
   */
  @Around("execution(* com.checkout.payment.gateway.client.BankClient.processPayment(..))")
  public Object aroundBankCall(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.nanoTime();
    Object result = joinPoint.proceed();
    long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    if (registry != null) {
      Timer.builder("bank.latency")
          .description("Bank API call latency")
          .publishPercentileHistogram()
          .register(registry)
          .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    return result;
  }

  /**
   * Around controller: count idempotency hits (deduplicated requests).
   */
  @Around("execution(* com.checkout.payment.gateway.controller.PaymentGatewayController.processPayment(..))")
  public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
    Object result = joinPoint.proceed();

    if (result instanceof ResponseEntity<?> entity
        && entity.getBody() instanceof PostPaymentResponse response
        && response.getId() != null) {
      // Service was not called — dedupe hit
      if (registry != null) {
        registry.counter("payment.dedupe.hit").increment();
      }
    }

    return result;
  }
}
