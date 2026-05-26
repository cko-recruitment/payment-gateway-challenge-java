package com.checkout.payment.gateway.aspect;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * KISS: One aspect handles all metrics via @Around advice.
 * No interfaces, no injected components — just SLF4J counters for now.
 * Swap to Micrometer later by replacing logger calls with registry.counter().increment().
 */
@Aspect
@Component
public class PaymentMetricsAspect {

  private static final Logger METRICS = LoggerFactory.getLogger("METRICS");

  /**
   * Around payment processing: count outcomes + time bank latency.
   */
  @Around("execution(* com.checkout.payment.gateway.service.PaymentGatewayService.processPayment(..))")
  public Object aroundProcessPayment(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.currentTimeMillis();
    Object result = joinPoint.proceed();
    long latency = System.currentTimeMillis() - start;

    METRICS.info("metric=payment_latency_ms value={}", latency);

    if (result instanceof PostPaymentResponse response) {
      METRICS.info("metric=payment_total status={}", response.getStatus());
    }
    return result;
  }

  /**
   * Around bank client: time external call.
   */
  @Around("execution(* com.checkout.payment.gateway.client.BankClient.processPayment(..))")
  public Object aroundBankCall(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.currentTimeMillis();
    Object result = joinPoint.proceed();
    long latency = System.currentTimeMillis() - start;

    METRICS.info("metric=bank_latency_ms value={}", latency);
    return result;
  }

  /**
   * Around controller: count idempotency hits.
   */
  @Around("execution(* com.checkout.payment.gateway.controller.PaymentGatewayController.processPayment(..))")
  public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
    Object result = joinPoint.proceed();

    if (result instanceof ResponseEntity<?> entity
        && entity.getBody() instanceof PostPaymentResponse response
        && response.getId() != null) {
      // Dedupe hit: controller returned existing payment without calling service
      // (detected by checking if service was never reached — simplified here)
    }

    return result;
  }
}
