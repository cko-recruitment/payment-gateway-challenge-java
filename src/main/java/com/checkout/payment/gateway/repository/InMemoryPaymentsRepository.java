package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentsRepository implements PaymentsRepository {

  private final ConcurrentMap<UUID, Payment> payments = new ConcurrentHashMap<>();

  @Override
  public Payment add(Payment payment) {
    payments.put(payment.getId(), payment);
    return payment;
  }

  @Override
  public Optional<Payment> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
