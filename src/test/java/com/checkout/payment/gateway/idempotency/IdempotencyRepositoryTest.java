package com.checkout.payment.gateway.idempotency;

import static com.checkout.payment.gateway.TestUtils.givenPaymentResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdempotencyRepositoryTest {

  private IdempotencyRepository testObject;

  @BeforeEach
  void setup() {
    testObject = new IdempotencyRepository();
  }

  @Test
  void whenDeleteAllShouldRemoveAllIdempotency() {
    // given
    UUID idempotencyKey = UUID.randomUUID();
    PaymentResponse response = givenPaymentResponse();
    testObject.save(idempotencyKey, response);

    // when
    testObject.deleteAll();

    // then
    assertThat(testObject.findAll()).isEmpty();
  }

  @Test
  void whenSaveShouldSaveIdempotency() {
    // given
    UUID idempotencyKey = UUID.randomUUID();
    PaymentResponse response = givenPaymentResponse();

    // when
    testObject.save(idempotencyKey, response);

    // then
    Optional<PaymentResponse> persistedResponse = testObject.findBy(idempotencyKey);

    assertThat(persistedResponse).isNotEmpty();
    assertThat(persistedResponse.get())
        .usingRecursiveComparison()
        .isEqualTo(response);
  }

  @Test
  void whenFindByShouldRetrieveIdempotency() {
    // given
    UUID idempotencyKey = UUID.randomUUID();
    PaymentResponse response = givenPaymentResponse();
    testObject.save(idempotencyKey, response);

    // when
    Optional<PaymentResponse> persistedResponse = testObject.findBy(idempotencyKey);

    // then
    assertThat(persistedResponse).isNotEmpty();
    assertThat(persistedResponse.get())
        .usingRecursiveComparison()
        .isEqualTo(response);
  }

}