package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {

  Optional<PostPaymentResponse> getByKey(String merchantId, String dedupeKey);

  Optional<PostPaymentResponse> getById(String merchantId, UUID id);

  void save(String merchantId, String dedupeKey, PostPaymentResponse response);
}
