package com.checkout.payment.gateway.aspect;

/**
 * TODO: Simple in-memory rate limiter per merchant.
 * KISS: Just a ConcurrentHashMap<String, AtomicInteger> with TTL cleanup.
 * No need for token bucket or AOP — check directly in Controller/Service.
 */
public interface RateLimiter {
  boolean allow(String merchantId);
}
