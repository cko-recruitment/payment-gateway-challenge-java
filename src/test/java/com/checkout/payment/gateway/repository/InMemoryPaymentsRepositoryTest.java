package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InMemoryPaymentsRepositoryTest {

  private InMemoryPaymentsRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryPaymentsRepository();
  }

  @Test
  void shouldSaveAndFindPayment() {
    UUID id = UUID.randomUUID();
    Payment payment = new Payment(
        id,
        PaymentStatus.AUTHORIZED,
        "4241",
        "USD",
        100,
        12,
        2029,
        "AUTH-123",
        Instant.now()
    );

    repository.add(payment);

    Optional<Payment> actual = repository.get(id);

    assertTrue(actual.isPresent());
    assertEquals(id, actual.get().getId());
    assertEquals("4241", actual.get().getMaskedCardNumber());
  }

  @Test
  void shouldReturnEmptyWhenPaymentNotFound() {
    Optional<Payment> actual = repository.get(UUID.randomUUID());
    assertTrue(actual.isEmpty());
  }
}
