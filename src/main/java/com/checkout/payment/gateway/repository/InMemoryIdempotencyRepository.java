package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryIdempotencyRepository implements IdempotencyRepository {

  private final ConcurrentHashMap<String, PostPaymentResponse> keyStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PostPaymentResponse> idStore = new ConcurrentHashMap<>();

  @Override
  public Optional<PostPaymentResponse> getByKey(String merchantId, String dedupeKey) {
    return Optional.ofNullable(keyStore.get(merchantId + ":" + dedupeKey));
  }

  @Override
  public Optional<PostPaymentResponse> getById(String merchantId, UUID id) {
    return Optional.ofNullable(idStore.get(merchantId + ":" + id.toString()));
  }

  @Override
  public void save(String merchantId, String dedupeKey, PostPaymentResponse response) {
    keyStore.put(merchantId + ":" + dedupeKey, response);
    if (response.getId() != null) {
      idStore.put(merchantId + ":" + response.getId().toString(), response);
    }
  }
}
