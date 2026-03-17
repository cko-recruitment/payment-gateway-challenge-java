package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of {@link PaymentsRepository} for storing payment records.
 * This repository uses a {@link ConcurrentHashMap} to store {@link Payment} objects,
 * keyed by their unique identifier ({@link UUID}). It is designed to be thread-safe
 * and suitable for concurrent access in a multi-threaded environment.

 * Note: This implementation is intended for demonstration or testing purposes only.
 * Data is stored in-memory and will be lost when the application restarts. It is not suitable
 * for production use where persistent storage is required.

 * Typical usage includes:
     - Storing newly created payments
     - Retrieving payments by their unique identifier
 */
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
