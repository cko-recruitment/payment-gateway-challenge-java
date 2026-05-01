package com.checkout.payment.gateway.payment.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, Payment> data = new ConcurrentHashMap<>();

  public void deleteAll() {
    data.clear();
  }

  public Payment save(Payment payment) {
    data.put(payment.id(), payment);
    return findById(payment.id()).orElseThrow();
  }

  public Optional<Payment> findById(UUID id) {
    return Optional.ofNullable(data.get(id));
  }

  public List<Payment> findAll() {
    return new ArrayList<>(data.values());
  }

}
