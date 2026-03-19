package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
  void save(PostPaymentResponse payment);
  Optional<PostPaymentResponse> saveIfAbsent(PostPaymentResponse payment);
  Optional<PostPaymentResponse> findById(UUID id);
}
