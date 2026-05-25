package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryIdempotencyRepository implements IdempotencyRepository {

  private final ConcurrentHashMap<String, PostPaymentResponse> store = new ConcurrentHashMap<>();

  @Override
  public Optional<PostPaymentResponse> getByKey(String merchantId, String dedupeKey) {
    return Optional.ofNullable(store.get(merchantId + ":" + dedupeKey));
  }

  @Override
  public void save(String merchantId, String dedupeKey, PostPaymentResponse response) {
    store.put(merchantId + ":" + dedupeKey, response);
  }
}
