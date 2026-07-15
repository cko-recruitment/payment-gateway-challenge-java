package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.IdempotentPaymentRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository implements IdempotencyStore {
  private final ConcurrentHashMap<String, IdempotentPaymentRecord> records = new ConcurrentHashMap<>();

  public Optional<IdempotentPaymentRecord> get(String idempotencyKey) {
    return Optional.ofNullable(records.get(idempotencyKey));
  }

  @Override
  public IdempotentPaymentRecord addIfAbsent(String idempotencyKey, IdempotentPaymentRecord record) {
    return records.putIfAbsent(idempotencyKey, record);
  }
}
