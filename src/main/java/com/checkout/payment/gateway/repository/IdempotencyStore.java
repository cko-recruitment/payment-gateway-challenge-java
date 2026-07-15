package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.IdempotentPaymentRecord;
import java.util.Optional;

public interface IdempotencyStore {
  Optional<IdempotentPaymentRecord> get(String idempotencyKey);

  IdempotentPaymentRecord addIfAbsent(String idempotencyKey, IdempotentPaymentRecord record);
}
