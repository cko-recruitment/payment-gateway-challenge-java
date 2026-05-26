package com.checkout.payment.gateway.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.stream.IntStream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

  private RateLimitAspect aspect;

  @BeforeEach
  void setUp() {
    aspect = new RateLimitAspect();
  }

  @Test
  void aroundController_shouldProceed_whenNoMerchantId() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    Object expected = new Object();
    when(joinPoint.proceed()).thenReturn(expected);

    // No request context set
    Object result = aspect.aroundController(joinPoint);

    assertEquals(expected, result);
    verify(joinPoint).proceed();
  }

  @Test
  void aroundController_shouldProceed_whenUnderLimit() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    Object expected = new Object();
    when(joinPoint.proceed()).thenReturn(expected);

    setMerchantId("merchant-1");

    Object result = aspect.aroundController(joinPoint);

    assertEquals(expected, result);
    verify(joinPoint).proceed();
  }

  @Test
  void aroundController_shouldReject_whenSecondWindowExceeded() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    setMerchantId("merchant-2");

    // Exhaust 1-second limit (10 requests)
    IntStream.range(0, 10).forEach(i -> {
      try {
        aspect.aroundController(joinPoint);
      } catch (Throwable ignored) {
      }
    });

    // 11th request should be rejected
    Object result = aspect.aroundController(joinPoint);

    assertTrue(result instanceof ResponseEntity);
    ResponseEntity<?> response = (ResponseEntity<?>) result;
    PostPaymentResponse body = (PostPaymentResponse) response.getBody();
    assertNotNull(body);
    assertEquals(PaymentStatus.REJECTED, body.getStatus());
    assertTrue(body.getDescription().contains("per second"));
  }

  @Test
  void aroundController_shouldTrackDifferentMerchantsIndependently() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenReturn(new Object());

    // Merchant A exhausts limit
    setMerchantId("merchant-a");
    IntStream.range(0, 10).forEach(i -> {
      try {
        aspect.aroundController(joinPoint);
      } catch (Throwable ignored) {
      }
    });

    // Merchant A rejected
    Object resultA = aspect.aroundController(joinPoint);
    assertTrue(resultA instanceof ResponseEntity);

    // Merchant B still allowed
    setMerchantId("merchant-b");
    Object resultB = aspect.aroundController(joinPoint);
    assertEquals(joinPoint.proceed(), resultB);
  }

  private void setMerchantId(String merchantId) {
    ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
    jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
    when(attrs.getRequest()).thenReturn(request);
    when(request.getHeader("Merchant-Id")).thenReturn(merchantId);
    RequestContextHolder.setRequestAttributes(attrs);
  }
}
