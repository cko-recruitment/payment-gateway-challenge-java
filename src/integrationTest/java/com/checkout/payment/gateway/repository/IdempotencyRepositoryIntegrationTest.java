package com.checkout.payment.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.GatewayCode;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IdempotencyRepositoryIntegrationTest {

  @Autowired
  private IdempotencyRepository idempotencyRepository;

  @Test
  void shouldSaveAndRetrievePaymentInSpringContext() {
    String merchantId = "merchant-int-1";
    String dedupeKey = "dedupe-int-1";
    UUID id = UUID.randomUUID();
    PostPaymentResponse response = buildResponse(id, PaymentStatus.AUTHORIZED);

    idempotencyRepository.save(merchantId, dedupeKey, response);

    Optional<PostPaymentResponse> byKey = idempotencyRepository.getByKey(merchantId, dedupeKey);
    Optional<PostPaymentResponse> byId = idempotencyRepository.getById(merchantId, id);

    assertThat(byKey).isPresent();
    assertThat(byKey.get().getId()).isEqualTo(id);
    assertThat(byId).isPresent();
    assertThat(byId.get().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void shouldIsolateMerchantsInSpringContext() {
    String dedupeKey = "shared-int-key";
    UUID idA = UUID.randomUUID();
    UUID idB = UUID.randomUUID();

    idempotencyRepository.save("merchant-a", dedupeKey, buildResponse(idA, PaymentStatus.AUTHORIZED));
    idempotencyRepository.save("merchant-b", dedupeKey, buildResponse(idB, PaymentStatus.DECLINED));

    Optional<PostPaymentResponse> resultA = idempotencyRepository.getByKey("merchant-a", dedupeKey);
    Optional<PostPaymentResponse> resultB = idempotencyRepository.getByKey("merchant-b", dedupeKey);

    assertThat(resultA).isPresent();
    assertThat(resultA.get().getId()).isEqualTo(idA);
    assertThat(resultB).isPresent();
    assertThat(resultB.get().getId()).isEqualTo(idB);
  }

  private PostPaymentResponse buildResponse(UUID id, PaymentStatus status) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(id);
    response.setStatus(status);
    response.setCardNumberLastFour(4242);
    response.setCurrency("USD");
    response.setAmount(100);
    response.setGatewayCode(status == PaymentStatus.AUTHORIZED ? GatewayCode.SUCCESS : GatewayCode.DECLINED);
    return response;
  }
}
