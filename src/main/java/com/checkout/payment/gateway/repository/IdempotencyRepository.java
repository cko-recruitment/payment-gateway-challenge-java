package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.IdempotentPaymentRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {
  private final ConcurrentHashMap<String, IdempotentPaymentRecord> records = new ConcurrentHashMap<>();

  public Optional<IdempotentPaymentRecord> get(String idempotencyKey) {
    return Optional.ofNullable(records.get(idempotencyKey));
  }

  public void add(String idempotencyKey, IdempotentPaymentRecord record) {
    records.put(idempotencyKey, record);
  }
}
