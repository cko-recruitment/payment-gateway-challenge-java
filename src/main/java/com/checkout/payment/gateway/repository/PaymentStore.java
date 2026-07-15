package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;

public interface PaymentStore {
  void add(PostPaymentResponse payment);

  Optional<PostPaymentResponse> get(UUID id);
}
