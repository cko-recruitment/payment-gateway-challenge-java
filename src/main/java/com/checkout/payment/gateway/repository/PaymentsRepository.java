package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentData;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository implements PaymentsPersistence {

  private final Map<UUID, PaymentData> payments = new ConcurrentHashMap<>();

  public void add(PaymentData payment) {
    payments.put(payment.id(), payment);
  }

  public Optional<PaymentData> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
