package com.checkout.payment.gateway.aspect;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Multi-granularity fixed window rate limiter.
 * Two windows per merchant: 1-second (smooth) + 60-second (ceiling).
 * Both must pass for the request to proceed.
 */
@Aspect
@Component
public class RateLimitAspect {

  // 1-second window: 10 req/s — smooth, prevents burst
  private static final int MAX_PER_SECOND = 10;
  private static final long WINDOW_SECONDS = 1;

  // 60-second window: 600 req/min — ceiling, protects downstream
  private static final int MAX_PER_MINUTE = 600;
  private static final long WINDOW_MINUTES = 60;

  private final Map<String, Window> secondWindows = new ConcurrentHashMap<>();
  private final Map<String, Window> minuteWindows = new ConcurrentHashMap<>();

  @Around("execution(* com.checkout.payment.gateway.controller.PaymentGatewayController.*(..))")
  public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
    String merchantId = getMerchantId();
    if (merchantId == null) {
      return joinPoint.proceed();
    }

    long now = Instant.now().getEpochSecond();

    // Check 1-second window (smooth)
    if (!allow(secondWindows, merchantId, now, WINDOW_SECONDS, MAX_PER_SECOND)) {
      return rejected("Rate limit exceeded: " + MAX_PER_SECOND + " requests per second");
    }

    // Check 60-second window (ceiling)
    if (!allow(minuteWindows, merchantId, now, WINDOW_MINUTES, MAX_PER_MINUTE)) {
      return rejected("Rate limit exceeded: " + MAX_PER_MINUTE + " requests per minute");
    }

    return joinPoint.proceed();
  }

  private boolean allow(Map<String, Window> windows, String merchantId, long now, long windowSize, int maxRequests) {
    long currentWindow = now / windowSize;
    Window window = windows.computeIfAbsent(merchantId, k -> new Window(currentWindow));

    if (window.start != currentWindow) {
      window.start = currentWindow;
      window.count.set(0);
    }

    return window.count.incrementAndGet() <= maxRequests;
  }

  private ResponseEntity<PostPaymentResponse> rejected(String message) {
    PostPaymentResponse rejected = new PostPaymentResponse();
    rejected.setStatus(com.checkout.payment.gateway.enums.PaymentStatus.REJECTED);
    rejected.setMessage(message);
    return new ResponseEntity<>(rejected, HttpStatus.OK);
  }

  private String getMerchantId() {
    try {
      ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        return attrs.getRequest().getHeader("Merchant-Id");
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static class Window {
    volatile long start;
    final AtomicInteger count;

    Window(long start) {
      this.start = start;
      this.count = new AtomicInteger(0);
    }
  }
}
