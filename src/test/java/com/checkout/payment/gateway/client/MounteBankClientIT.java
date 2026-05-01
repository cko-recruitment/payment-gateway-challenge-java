package com.checkout.payment.gateway.client;

import static com.checkout.payment.gateway.TestUtils.givenPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.IntegrationTest;
import com.checkout.payment.gateway.client.dto.MounteBankResponse;
import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.shared.exception.MounteBankException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

class MounteBankClientIT extends IntegrationTest {

  @Autowired
  private MounteBankClient testObject;

  @Nested
  class Authorized {

    @Test
    void whenCardEndsWithOneShouldReturnAuthorized() {
      // given
      Payment payment = givenPayment("2222405343248881");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isTrue();
      assertThat(response.authorizationCode()).isNotNull();
    }

    @Test
    void whenCardEndsWithThreeShouldReturnAuthorized() {
      // given
      Payment payment = givenPayment("2222405343248883");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isTrue();
      assertThat(response.authorizationCode()).isNotNull();
    }

    @Test
    void whenCardEndsWithFiveShouldReturnAuthorized() {
      // given
      Payment payment = givenPayment("2222405343248885");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isTrue();
      assertThat(response.authorizationCode()).isNotNull();
    }

    @Test
    void whenCardEndsWithSevenShouldReturnAuthorized() {
      // given
      Payment payment = givenPayment("2222405343248887");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isTrue();
      assertThat(response.authorizationCode()).isNotNull();
    }

    @Test
    void whenCardEndsWithNineShouldReturnAuthorized() {
      // given
      Payment payment = givenPayment("2222405343248889");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isTrue();
      assertThat(response.authorizationCode()).isNotNull();
    }
  }

  @Nested
  class Declined {

    @Test
    void whenCardEndsWithTwoShouldReturnDeclined() {
      // given
      Payment payment = givenPayment("2222405343248882");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isFalse();
      assertThat(response.authorizationCode()).isNull();
    }

    @Test
    void whenCardEndsWithFourShouldReturnDeclined() {
      // given
      Payment payment = givenPayment("2222405343248884");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isFalse();
      assertThat(response.authorizationCode()).isNull();
    }

    @Test
    void whenCardEndsWithSixShouldReturnDeclined() {
      // given
      Payment payment = givenPayment("2222405343248886");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isFalse();
      assertThat(response.authorizationCode()).isNull();
    }

    @Test
    void whenCardEndsWithEightShouldReturnDeclined() {
      // given
      Payment payment = givenPayment("2222405343248888");

      // when
      MounteBankResponse response = testObject.executePayment(payment);

      // then
      assertThat(response.authorized()).isFalse();
      assertThat(response.authorizationCode()).isNull();
    }
  }

  @Nested
  class BankErrors {

    @Test
    void whenCardEndsWithZeroShouldThrowMounteBankException() {
      // given
      Payment payment = givenPayment("2222405343248880");

      // when then
      assertThatThrownBy(() -> testObject.executePayment(payment))
          .isInstanceOf(MounteBankException.class)
          .rootCause()
          .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }
  }

  @Nested
  class Resiliency {

    @Autowired
    private RetryRegistry retryRegistry;

    @Test
    void whenBankReturns503ShouldRetryThreeTimes() {
      // given
      Payment payment = givenPayment("2222405343248880");
      Retry retry = retryRegistry.retry("mounteBank");
      AtomicInteger attemptCount = new AtomicInteger(0);
      retry.getEventPublisher().onRetry(e -> attemptCount.incrementAndGet());

      // when then
      assertThatThrownBy(() -> testObject.executePayment(payment))
          .isInstanceOf(MounteBankException.class);

      // then
      assertThat(attemptCount.get()).isEqualTo(2);
    }

    @Test
    void whenBankReturns400ShouldNotRetry() {
      // given
      Payment payment = givenPayment(null);
      Retry retry = retryRegistry.retry("mounteBank");
      AtomicInteger attemptCount = new AtomicInteger(0);
      retry.getEventPublisher().onRetry(e -> attemptCount.incrementAndGet());

      // when then
      assertThatThrownBy(() -> testObject.executePayment(payment))
          .isInstanceOf(HttpClientErrorException.BadRequest.class);

      // then - no retries fired for 4xx
      assertThat(attemptCount.get()).isEqualTo(0);
    }
  }
}