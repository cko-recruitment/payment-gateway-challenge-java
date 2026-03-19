package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In memory adapter for {@link PaymentRepository}
 */
@Repository
public class InMemoryPaymentRepositoryImpl implements PaymentRepository {

  private final Map<UUID, PostPaymentResponse> payments = new ConcurrentHashMap<>();

  @Override
  public void save(PostPaymentResponse payment) {
    add(payment);
  }

  @Override
  public Optional<PostPaymentResponse> saveIfAbsent(PostPaymentResponse payment) {
    PostPaymentResponse existing = payments.putIfAbsent(payment.paymentId(), payment);
    return Optional.ofNullable(existing);
  }

  @Override
  public Optional<PostPaymentResponse> findById(UUID paymentId) {
    return get(paymentId);
  }

  private void add(PostPaymentResponse payment) {
    payments.put(payment.paymentId(), payment);
  }

  private Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
