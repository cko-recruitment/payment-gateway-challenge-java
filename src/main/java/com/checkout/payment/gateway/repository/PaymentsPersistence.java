package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentData;
import java.util.Optional;
import java.util.UUID;

public interface PaymentsPersistence {

  void add(PaymentData payment);

  Optional<PaymentData> get(UUID id);
}
