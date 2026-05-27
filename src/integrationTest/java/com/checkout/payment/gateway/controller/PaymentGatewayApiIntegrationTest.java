package com.checkout.payment.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test: verifies the deployed API endpoints are reachable
 * and return expected responses after the application starts.
 * Uses random port to avoid conflicts with other tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "bank.url=http://localhost:8080/payments")
class PaymentGatewayApiIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void shouldProcessPaymentAndRetrieveById() {
    String merchantId = "merchant-int-api";
    String dedupeKey = UUID.randomUUID().toString();
    PostPaymentRequest request = buildRequest("4242424242424241", "USD", 100);

    // POST /payments
    ResponseEntity<PostPaymentResponse> postResponse = postPayment(merchantId, dedupeKey, request);
    assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(postResponse.getBody()).isNotNull();
    assertThat(postResponse.getBody().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    UUID paymentId = postResponse.getBody().getId();

    // GET /payments/{id}
    ResponseEntity<GetPaymentResponse> getResponse = getPaymentById(merchantId, paymentId);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).isNotNull();
    assertThat(getResponse.getBody().getId()).isEqualTo(paymentId);
  }

  @Test
  void shouldReturnDedupeResponseForSameKey() {
    String merchantId = "merchant-int-dedupe";
    String dedupeKey = "dedupe-int-key";
    PostPaymentRequest request = buildRequest("4242424242424242", "GBP", 200);

    ResponseEntity<PostPaymentResponse> first = postPayment(merchantId, dedupeKey, request);
    ResponseEntity<PostPaymentResponse> second = postPayment(merchantId, dedupeKey, request);

    assertThat(first.getBody().getId()).isEqualTo(second.getBody().getId());
    
  }

  @Test
  void shouldRetrieveByIdempotencyKey() {
    String merchantId = "merchant-int-idem";
    String dedupeKey = "idem-int-key";
    PostPaymentRequest request = buildRequest("4242424242424241", "USD", 300);

    ResponseEntity<PostPaymentResponse> post = postPayment(merchantId, dedupeKey, request);
    UUID paymentId = post.getBody().getId();

    ResponseEntity<GetPaymentResponse> get = getPaymentByIdempotencyKey(merchantId, dedupeKey);

    assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get.getBody().getId()).isEqualTo(paymentId);
  }

  private ResponseEntity<PostPaymentResponse> postPayment(String merchantId, String dedupeKey, PostPaymentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Merchant-Id", merchantId);
    headers.set("Idempotency-Key", dedupeKey);
    HttpEntity<PostPaymentRequest> entity = new HttpEntity<>(request, headers);
    return restTemplate.postForEntity(baseUrl() + "/api/v1/payments", entity, PostPaymentResponse.class);
  }

  private ResponseEntity<GetPaymentResponse> getPaymentById(String merchantId, UUID id) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Merchant-Id", merchantId);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(baseUrl() + "/api/v1/payments/" + id, org.springframework.http.HttpMethod.GET, entity, GetPaymentResponse.class);
  }

  private ResponseEntity<GetPaymentResponse> getPaymentByIdempotencyKey(String merchantId, String key) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Merchant-Id", merchantId);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(baseUrl() + "/api/v1/payments/idempotency/" + key, org.springframework.http.HttpMethod.GET, entity, GetPaymentResponse.class);
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private PostPaymentRequest buildRequest(String cardNumber, String currency, int amount) {
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
