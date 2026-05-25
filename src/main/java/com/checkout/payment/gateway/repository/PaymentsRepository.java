package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final HashMap<UUID, PaymentResponse> payments = new HashMap<>();

  public void add(PaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  public Optional<PaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
