package com.checkout.payment.gateway.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentMetricsAspectTest {

  private PaymentMetricsAspect aspect;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    aspect = new PaymentMetricsAspect(registry);
  }

  @Test
  void aroundProcessPayment_shouldRecordLatencyAndStatus() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    PostPaymentResponse response = new PostPaymentResponse();
    response.setStatus(PaymentStatus.AUTHORIZED);
    when(joinPoint.proceed()).thenReturn(response);

    Object result = aspect.aroundProcessPayment(joinPoint);

    assertSame(response, result);
    verify(joinPoint).proceed();

    // Verify Micrometer metrics
    assertEquals(1, registry.counter("payment.total", "status", "AUTHORIZED").count());
    assertEquals(1, registry.get("payment.latency").tag("status", "AUTHORIZED").timer().count());
  }

  @Test
  void aroundProcessPayment_shouldHandleNonResponseResult() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn("not a response");

    Object result = aspect.aroundProcessPayment(joinPoint);

    assertEquals("not a response", result);
    verify(joinPoint).proceed();
  }

  @Test
  void aroundBankCall_shouldRecordLatency() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    Object expectedResult = new Object();
    when(joinPoint.proceed()).thenReturn(expectedResult);

    Object result = aspect.aroundBankCall(joinPoint);

    assertSame(expectedResult, result);
    verify(joinPoint).proceed();

    // Verify bank latency metric
    assertEquals(1, registry.get("bank.latency").timer().count());
  }

  @Test
  void aroundController_shouldProceedNormally() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(java.util.UUID.randomUUID());
    org.springframework.http.ResponseEntity<PostPaymentResponse> entity =
        org.springframework.http.ResponseEntity.ok(response);
    when(joinPoint.proceed()).thenReturn(entity);

    Object result = aspect.aroundController(joinPoint);

    assertSame(entity, result);
    verify(joinPoint).proceed();

    // Verify dedupe metric
    assertEquals(1, registry.counter("payment.dedupe.hit").count());
  }

  @Test
  void aroundController_shouldHandleNonEntityResult() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn("raw string");

    Object result = aspect.aroundController(joinPoint);

    assertEquals("raw string", result);
    verify(joinPoint).proceed();
  }
}
