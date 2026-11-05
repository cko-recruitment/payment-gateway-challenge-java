package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentData;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository implements PaymentsPersistence {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentsRepository.class);

  private final Map<UUID, PaymentData> payments = new ConcurrentHashMap<>();

  public void add(PaymentData payment) {
    payments.put(payment.id(), payment);
    LOG.debug("Stored payment {}; {} payment(s) held", payment.id(), payments.size());
  }

  public Optional<PaymentData> get(UUID id) {
    Optional<PaymentData> payment = Optional.ofNullable(payments.get(id));
    LOG.debug("Lookup of payment {}: {}", id, payment.isPresent() ? "found" : "not found");
    return payment;
  }

}
