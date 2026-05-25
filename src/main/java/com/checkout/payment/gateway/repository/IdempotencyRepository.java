package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;

public interface IdempotencyRepository {

  Optional<PostPaymentResponse> getByKey(String merchantId, String dedupeKey);

  void save(String merchantId, String dedupeKey, PostPaymentResponse response);
}
