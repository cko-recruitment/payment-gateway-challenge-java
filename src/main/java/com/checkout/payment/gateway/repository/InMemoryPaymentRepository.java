package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

  private final ConcurrentHashMap<UUID, PostPaymentResponse> payments = new ConcurrentHashMap<>();

  @Override
  public void add(PostPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  @Override
  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
