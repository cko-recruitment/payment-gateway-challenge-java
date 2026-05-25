package com.checkout.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
class PaymentGatewayFunctionalTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void shouldAuthorizePaymentWithOddCardNumber() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "USD", 1050);

    ResponseEntity<PostPaymentResponse> response = postPayment("merchant-123", UUID.randomUUID().toString(),request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getBody().getId()).isNotNull();
    assertThat(response.getBody().getCardNumberLastFour()).isEqualTo(4241);
    assertThat(response.getBody().getCurrency()).isEqualTo("USD");
    assertThat(response.getBody().getAmount()).isEqualTo(1050);
  }

  @Test
  void shouldDeclinePaymentWithEvenCardNumber() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424242", "GBP", 2500);

    ResponseEntity<PostPaymentResponse> response = postPayment("merchant-123", UUID.randomUUID().toString(),request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getBody().getId()).isNotNull();
    assertThat(response.getBody().getCurrency()).isEqualTo("GBP");
  }

  @Test
  void shouldRejectPaymentWithInvalidCardNumber() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424abc", "USD", 100);

    ResponseEntity<PostPaymentResponse> response = postPayment("merchant-123", UUID.randomUUID().toString(),request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).contains("Card number must only contain numeric characters");
  }

  @Test
  void shouldRejectPaymentWithInvalidExpiryDate() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424242", "USD", 100);
    request.setExpiryMonth(13);
    request.setExpiryYear(YearMonth.now().getYear());

    ResponseEntity<PostPaymentResponse> response = postPayment("merchant-123", UUID.randomUUID().toString(),request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).isNotBlank();
  }

  @Test
  void shouldRejectPaymentWithInvalidCurrency() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424242", "840", 1500);

    ResponseEntity<PostPaymentResponse> response = postPayment("merchant-123",UUID.randomUUID().toString(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).contains("Invalid ISO 4217 currency code");
  }

  @Test
  void shouldRejectPaymentWithMultipleValidationErrors() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("abc");
    request.setExpiryMonth(13);
    request.setExpiryYear(YearMonth.now().getYear() - 1);
    request.setCurrency("US");
    request.setAmount(-10);
    request.setCvv("12");

    ResponseEntity<PostPaymentResponse> response = postPayment("merchant-123", UUID.randomUUID().toString(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).contains(";");
  }

  @Test
  void shouldReturnSameResponseForSameDedupeKey() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "USD", 100);
    String merchantId = "merchant-abc";
    String dedupeKey = "dedupe-key-123";

    ResponseEntity<PostPaymentResponse> response1 = postPayment(merchantId, dedupeKey, request);
    ResponseEntity<PostPaymentResponse> response2 = getPaymentByIdempotencyKey(merchantId, dedupeKey);

    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response1.getBody().getId()).isEqualTo(response2.getBody().getId());
  }

  @Test
  void shouldRetrievePaymentBySystemId() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "USD", 100);
    String merchantId = "merchant-id-test";
    String dedupeKey = UUID.randomUUID().toString();

    ResponseEntity<PostPaymentResponse> created = postPayment(merchantId, dedupeKey, request);
    UUID paymentId = created.getBody().getId();

    ResponseEntity<PostPaymentResponse> retrieved = getPaymentById(merchantId, paymentId);

    assertThat(retrieved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(retrieved.getBody().getId()).isEqualTo(paymentId);
  }

  @Test
  void shouldCreateDifferentPaymentsForDifferentMerchantsWithSameDedupeKey() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "USD", 100);
    String dedupeKey = "dedupe-key-shared";

    ResponseEntity<PostPaymentResponse> response1 = postPayment("merchant-a", dedupeKey, request);
    ResponseEntity<PostPaymentResponse> response2 = postPayment("merchant-b", dedupeKey, request);

    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response1.getBody().getId()).isNotEqualTo(response2.getBody().getId());
  }


  private ResponseEntity<PostPaymentResponse> postPayment(String merchantId, String dedupeKey, PostPaymentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Merchant-Id", merchantId);
    if (dedupeKey != null) {
      headers.set("Idempotency-Key", dedupeKey);
    }
    HttpEntity<PostPaymentRequest> entity = new HttpEntity<>(request, headers);
    return restTemplate.postForEntity("/api/v1/payments", entity, PostPaymentResponse.class);
  }

  private ResponseEntity<PostPaymentResponse> getPaymentByIdempotencyKey(String merchantId, String dedupeKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Merchant-Id", merchantId);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange("/api/v1/payments/idempotency/" + dedupeKey, org.springframework.http.HttpMethod.GET, entity, PostPaymentResponse.class);
  }

  private ResponseEntity<PostPaymentResponse> getPaymentById(String merchantId, UUID id) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Merchant-Id", merchantId);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange("/api/v1/payments/" + id, org.springframework.http.HttpMethod.GET, entity, PostPaymentResponse.class);
  }

  private PostPaymentRequest buildPaymentRequest(String cardNumber, String currency, int amount) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(YearMonth.now().getMonthValue());
    request.setExpiryYear(YearMonth.now().getYear() + 1);
    request.setCurrency(currency);
    request.setAmount(amount);
    request.setCvv("123");
    return request;
  }
}
