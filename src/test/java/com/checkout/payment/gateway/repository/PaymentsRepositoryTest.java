package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  private final PaymentsRepository repository = new PaymentsRepository();

  @Test
  void retrievesEachPaymentByItsOwnId() {
    PostPaymentResponse authorized = payment(PaymentStatus.AUTHORIZED);
    PostPaymentResponse declined = payment(PaymentStatus.DECLINED);

    repository.add(authorized);
    repository.add(declined);

    assertEquals(authorized, repository.get(authorized.id()).orElseThrow());
    assertEquals(declined, repository.get(declined.id()).orElseThrow());
  }

  @Test
  void unknownIdReturnsEmpty() {
    assertTrue(repository.get(UUID.randomUUID()).isEmpty());
  }

  private PostPaymentResponse payment(PaymentStatus status) {
    return new PostPaymentResponse(UUID.randomUUID(), status, "0001", 12, 2099, "GBP", 10);
  }
}
