package com.checkout.payment.gateway.payment;

import static com.checkout.payment.gateway.TestUtils.givenPayment;
import static com.checkout.payment.gateway.TestUtils.givenValidPaymentRequest;

import com.checkout.payment.gateway.IntegrationTest;
import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentStatusEnum;
import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.payment.entity.PaymentStatus;
import com.checkout.payment.gateway.payment.entity.PaymentsRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
class PaymentsControllerIT extends IntegrationTest {

  @Autowired
  private RestTestClient restTestClient;

  @Autowired
  private PaymentsRepository paymentsRepository;

  @Nested
  class ProcessPayment {

    @Test
    void whenValidRequestWithOddCardShouldReturnAuthorized() {
      // given
      PaymentRequest request = givenValidPaymentRequest()
          .cardNumber("2222405343248881");

      // when then
      restTestClient.post()
          .uri("/v1/payments")
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .body(request)
          .exchange()
          .expectStatus().isCreated()
          .expectBody()
          .jsonPath("$.id").isNotEmpty()
          .jsonPath("$.status").isEqualTo(PaymentStatusEnum.AUTHORIZED.getValue())
          .jsonPath("$.cardNumberLastFour").isEqualTo("8881")
          .jsonPath("$.amount").isEqualTo(request.getAmount())
          .jsonPath("$.currency").isEqualTo(request.getCurrency().getValue())
          .jsonPath("$.expiryMonth").isEqualTo(request.getExpiryMonth())
          .jsonPath("$.expiryYear").isEqualTo(request.getExpiryYear());
    }

    @Test
    void whenValidRequestWithEvenCardShouldReturnDeclined() {
      // given
      PaymentRequest request = givenValidPaymentRequest()
          .cardNumber("2222405343248882");

      // when then
      restTestClient.post()
          .uri("/v1/payments")
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .body(request)
          .exchange()
          .expectStatus().isCreated()
          .expectBody()
          .jsonPath("$.id").isNotEmpty()
          .jsonPath("$.status").isEqualTo(PaymentStatusEnum.DECLINED.getValue())
          .jsonPath("$.cardNumberLastFour").isEqualTo("8882")
          .jsonPath("$.amount").isEqualTo(request.getAmount())
          .jsonPath("$.currency").isEqualTo(request.getCurrency().getValue())
          .jsonPath("$.expiryMonth").isEqualTo(request.getExpiryMonth())
          .jsonPath("$.expiryYear").isEqualTo(request.getExpiryYear());
    }

    @Test
    void whenSameIdempotencyKeyUsedShouldReturnExistingPayment() throws Exception {
      // given
      String idempotencyKey = UUID.randomUUID().toString();
      PaymentRequest request = givenValidPaymentRequest()
          .cardNumber("2222405343248881");

      byte[] firstResponseBody = restTestClient.post()
          .uri("/v1/payments")
          .header("Idempotency-Key", idempotencyKey)
          .body(request)
          .exchange()
          .expectStatus().isCreated()
          .expectBody()
          .jsonPath("$.id").isNotEmpty()
          .returnResult()
          .getResponseBody();

      String paymentId = JsonPath.parse(new String(firstResponseBody)).read("$.id");

      // when then
      restTestClient.post()
          .uri("/v1/payments")
          .header("Idempotency-Key", idempotencyKey)
          .body(request)
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.id").isEqualTo(paymentId);
    }
  }

  @Nested
  class GetPayment {

    @Test
    void whenAuthorizedPaymentShouldBeRetrievable() {
      // given - persist directly via repository
      Payment payment = givenPayment("2222405343248881");
      paymentsRepository.save(payment);

      // when then
      restTestClient.get()
          .uri("/v1/payments/{id}", payment.id())
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.id").isEqualTo(payment.id().toString())
          .jsonPath("$.status").isEqualTo(PaymentStatusEnum.AUTHORIZED.getValue())
          .jsonPath("$.cardNumberLastFour").isEqualTo("8881")
          .jsonPath("$.amount").isEqualTo(payment.amount())
          .jsonPath("$.expiryMonth").isEqualTo(payment.expiryMonth())
          .jsonPath("$.expiryYear").isEqualTo(payment.expiryYear());
    }

    @Test
    void whenDeclinedPaymentShouldBeRetrievable() {
      // given - persist directly via repository
      Payment payment = givenPayment("2222405343248882")
          .toBuilder()
          .status(PaymentStatus.DECLINED)
          .build();
      paymentsRepository.save(payment);

      // when then
      restTestClient.get()
          .uri("/v1/payments/{id}", payment.id())
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.id").isEqualTo(payment.id().toString())
          .jsonPath("$.status").isEqualTo(PaymentStatusEnum.DECLINED.getValue())
          .jsonPath("$.cardNumberLastFour").isEqualTo("8882")
          .jsonPath("$.amount").isEqualTo(payment.amount())
          .jsonPath("$.expiryMonth").isEqualTo(payment.expiryMonth())
          .jsonPath("$.expiryYear").isEqualTo(payment.expiryYear());
    }

    @Test
    void whenPaymentDoesNotExistShouldReturnNotFound() {
      // when then
      restTestClient.get()
          .uri("/v1/payments/{id}", UUID.randomUUID())
          .exchange()
          .expectStatus()
          .isNotFound();
    }
  }
}
