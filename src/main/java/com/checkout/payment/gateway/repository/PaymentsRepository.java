package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentData;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository implements PaymentsPersistence {

  private final HashMap<UUID, PaymentData> payments = new HashMap<>();

  public void add(PaymentData payment) {
    payments.put(payment.id(), payment);
  }

  public Optional<PaymentData> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
