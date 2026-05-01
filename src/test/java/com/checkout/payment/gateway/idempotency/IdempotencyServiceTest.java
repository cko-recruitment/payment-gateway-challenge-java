package com.checkout.payment.gateway.idempotency;

import static com.checkout.payment.gateway.TestUtils.givenPaymentResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

  @Mock
  private IdempotencyRepository idempotencyRepository;

  private IdempotencyService testObject;

  @BeforeEach
  void setup() {
    this.testObject = new IdempotencyService(idempotencyRepository);
  }

  @Nested
  class FindBy {

    @Test
    void whenIdempotencyKeyExistsShouldReturnPaymentResponse() {
      // given
      UUID idempotencyKey = UUID.randomUUID();
      PaymentResponse response = givenPaymentResponse();
      given(idempotencyRepository.findBy(idempotencyKey)).willReturn(Optional.of(response));

      // when
      Optional<PaymentResponse> result = testObject.findBy(idempotencyKey);

      // then
      assertThat(result).isPresent().contains(response);
    }

    @Test
    void whenIdempotencyKeyDoesNotExistShouldReturnEmpty() {
      // given
      UUID idempotencyKey = UUID.randomUUID();
      given(idempotencyRepository.findBy(idempotencyKey)).willReturn(Optional.empty());

      // when
      Optional<PaymentResponse> result = testObject.findBy(idempotencyKey);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class Save {

    @Test
    void whenSaveCalledShouldDelegateToRepository() {
      // given
      UUID idempotencyKey = UUID.randomUUID();
      PaymentResponse response = givenPaymentResponse();

      // when
      testObject.save(idempotencyKey, response);

      // then
      then(idempotencyRepository).should().save(idempotencyKey, response);
    }
  }
}