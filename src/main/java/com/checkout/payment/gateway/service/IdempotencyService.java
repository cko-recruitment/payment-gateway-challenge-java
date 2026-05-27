package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Thread-safe idempotency service with distributed locking concept.
 * In production, use Redis RedLock or database unique constraint.
 */
@Service
public class IdempotencyService {

  // In-memory store for demo. Production: Redis or Database
  private final ConcurrentHashMap<String, PaymentLock> locks = new ConcurrentHashMap<>();

  /**
   * Acquire lock for processing. Returns true if acquired, false if already processing.
   */
  public boolean acquireLock(String merchantId, String dedupeKey) {
    String key = merchantId + ":" + dedupeKey;
    PaymentLock newLock = new PaymentLock();
    PaymentLock existing = locks.putIfAbsent(key, newLock);
    
    if (existing == null) {
      // We acquired the lock
      newLock.lock();
      return true;
    }
    
    // Someone else is processing, wait for result
    return false;
  }

  /**
   * Wait for existing processing to complete and return result.
   */
  public Optional<PostPaymentResponse> waitForResult(String merchantId, String dedupeKey, long timeoutMs) {
    String key = merchantId + ":" + dedupeKey;
    PaymentLock lock = locks.get(key);
    
    if (lock == null) {
      return Optional.empty();
    }
    
    try {
      // Wait for processing to complete
      boolean completed = lock.await(timeoutMs);
      if (completed) {
        return Optional.ofNullable(lock.getResult());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    
    return Optional.empty();
  }

  /**
   * Store result and release lock.
   */
  public void completeAndRelease(String merchantId, String dedupeKey, PostPaymentResponse result) {
    String key = merchantId + ":" + dedupeKey;
    PaymentLock lock = locks.get(key);
    
    if (lock != null) {
      lock.setResult(result);
      lock.unlock();
      locks.remove(key);
    }
  }

  /**
   * Simple lock with condition for coordination.
   */
  private static class PaymentLock {
    private final ReentrantLock lock = new ReentrantLock();
    private final java.util.concurrent.locks.Condition done = lock.newCondition();
    private volatile PostPaymentResponse result;
    private volatile boolean completed = false;

    public void lock() {
      this.lock.lock();
    }

    public void unlock() {
      this.lock.lock();
      try {
        this.completed = true;
        this.done.signalAll();
      } finally {
        this.lock.unlock();
      }
    }

    public boolean await(long timeoutMs) throws InterruptedException {
      this.lock.lock();
      try {
        if (this.completed) {
          return true;
        }
        return this.done.await(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
        this.lock.unlock();
      }
    }

    public PostPaymentResponse getResult() {
      return result;
    }

    public void setResult(PostPaymentResponse result) {
      this.result = result;
    }
  }
}
