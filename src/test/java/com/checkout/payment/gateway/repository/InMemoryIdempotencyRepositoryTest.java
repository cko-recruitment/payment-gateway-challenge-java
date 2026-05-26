package com.checkout.payment.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.GatewayCode;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyRepositoryTest {

  private InMemoryIdempotencyRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryIdempotencyRepository();
  }

  @Test
  void shouldSaveAndRetrieveByKey() {
    String merchantId = "merchant-123";
    String dedupeKey = "dedupe-key-1";
    PostPaymentResponse response = buildResponse(UUID.randomUUID(), PaymentStatus.AUTHORIZED);

    repository.save(merchantId, dedupeKey, response);

    Optional<PostPaymentResponse> result = repository.getByKey(merchantId, dedupeKey);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void shouldReturnEmptyWhenKeyNotFound() {
    Optional<PostPaymentResponse> result = repository.getByKey("merchant-123", "non-existent");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldRetrieveById() {
    String merchantId = "merchant-123";
    UUID id = UUID.randomUUID();
    String dedupeKey = "dedupe-key-2";
    PostPaymentResponse response = buildResponse(id, PaymentStatus.AUTHORIZED);

    repository.save(merchantId, dedupeKey, response);

    Optional<PostPaymentResponse> result = repository.getById(merchantId, id);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(id);
  }

  @Test
  void shouldReturnEmptyWhenIdNotFound() {
    Optional<PostPaymentResponse> result = repository.getById("merchant-123", UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldIsolateDataBetweenMerchants() {
    String merchantA = "merchant-a";
    String merchantB = "merchant-b";
    String dedupeKey = "shared-key";
    UUID idA = UUID.randomUUID();
    UUID idB = UUID.randomUUID();

    repository.save(merchantA, dedupeKey, buildResponse(idA, PaymentStatus.AUTHORIZED));
    repository.save(merchantB, dedupeKey, buildResponse(idB, PaymentStatus.DECLINED));

    Optional<PostPaymentResponse> resultA = repository.getByKey(merchantA, dedupeKey);
    Optional<PostPaymentResponse> resultB = repository.getByKey(merchantB, dedupeKey);

    assertThat(resultA).isPresent();
    assertThat(resultA.get().getId()).isEqualTo(idA);
    assertThat(resultB).isPresent();
    assertThat(resultB.get().getId()).isEqualTo(idB);
  }

  @Test
  void shouldOverwriteExistingKey() {
    String merchantId = "merchant-123";
    String dedupeKey = "dedupe-key-3";
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();

    repository.save(merchantId, dedupeKey, buildResponse(firstId, PaymentStatus.AUTHORIZED));
    repository.save(merchantId, dedupeKey, buildResponse(secondId, PaymentStatus.DECLINED));

    Optional<PostPaymentResponse> result = repository.getByKey(merchantId, dedupeKey);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(secondId);
    assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.DECLINED);
  }

  @Test
  void shouldNotStoreByIdWhenIdIsNull() {
    String merchantId = "merchant-123";
    String dedupeKey = "dedupe-key-4";
    PostPaymentResponse response = new PostPaymentResponse();
    response.setStatus(PaymentStatus.REJECTED);
    // id is null

    repository.save(merchantId, dedupeKey, response);

    Optional<PostPaymentResponse> byKey = repository.getByKey(merchantId, dedupeKey);
    assertThat(byKey).isPresent();

    // Cannot lookup by null id
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
