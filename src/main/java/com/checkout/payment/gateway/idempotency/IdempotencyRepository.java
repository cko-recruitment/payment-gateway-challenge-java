package com.checkout.payment.gateway.idempotency;

import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

  private final ConcurrentHashMap<UUID, PaymentResponse> data = new ConcurrentHashMap<>();

  public void deleteAll() {
    data.clear();
  }

  public void save(UUID idempotencyKey, PaymentResponse response) {
    data.put(idempotencyKey, response);
  }

  public Optional<PaymentResponse> findBy(UUID idempotencyKey) {
    return Optional.ofNullable(data.get(idempotencyKey));
  }

  public List<PaymentResponse> findAll() {
    return new ArrayList<>(data.values());
  }
}
