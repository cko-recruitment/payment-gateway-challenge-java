package com.checkout.payment.gateway.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentGatewayApiE2ETest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void whenBankSimulatorIsAvailableThenCanProcessAndRetrievePayment() throws Exception {
    assumeTrue(isBankSimulatorAvailable(), "Bank simulator is not running on localhost:8080");

    HttpHeaders headers = jsonHeaders();
    String requestBody =
        "{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}";

    ResponseEntity<String> postResponse = testRestTemplate.exchange(
        "/payment",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    assertEquals(HttpStatus.OK, postResponse.getStatusCode());
    JsonNode payment = objectMapper.readTree(postResponse.getBody());
    String paymentId = payment.get("id").asText();
    assertEquals("Authorized", payment.get("status").asText());
    assertEquals(8877, payment.get("cardNumberLastFour").asInt());

    ResponseEntity<String> getResponse = testRestTemplate.getForEntity("/payment/" + paymentId, String.class);
    assertEquals(HttpStatus.OK, getResponse.getStatusCode());
    JsonNode fetchedPayment = objectMapper.readTree(getResponse.getBody());
    assertEquals(paymentId, fetchedPayment.get("id").asText());
    assertEquals("Authorized", fetchedPayment.get("status").asText());
  }

  @Test
  void whenUsingSameIdempotencyKeyThenOriginalPaymentIsReplayed() throws Exception {
    assumeTrue(isBankSimulatorAvailable(), "Bank simulator is not running on localhost:8080");

    String idempotencyKey = "e2e-idem-" + UUID.randomUUID();
    HttpHeaders headers = jsonHeaders();
    headers.set("Idempotency-Key", idempotencyKey);
    String requestBody =
        "{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}";

    ResponseEntity<String> first = testRestTemplate.exchange(
        "/payment",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );
    ResponseEntity<String> second = testRestTemplate.exchange(
        "/payment",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    assertEquals(HttpStatus.OK, first.getStatusCode());
    assertEquals(HttpStatus.OK, second.getStatusCode());
    JsonNode firstBody = objectMapper.readTree(first.getBody());
    JsonNode secondBody = objectMapper.readTree(second.getBody());
    assertEquals(firstBody.get("id").asText(), secondBody.get("id").asText());
  }

  @Test
  void whenUsingSameIdempotencyKeyWithDifferentRequestThenConflictIsReturned() throws Exception {
    assumeTrue(isBankSimulatorAvailable(), "Bank simulator is not running on localhost:8080");

    String idempotencyKey = "e2e-idem-" + UUID.randomUUID();
    HttpHeaders headers = jsonHeaders();
    headers.set("Idempotency-Key", idempotencyKey);

    ResponseEntity<String> first = testRestTemplate.exchange(
        "/payment",
        HttpMethod.POST,
        new HttpEntity<>(
            "{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}",
            headers
        ),
        String.class
    );

    ResponseEntity<String> second = testRestTemplate.exchange(
        "/payment",
        HttpMethod.POST,
        new HttpEntity<>(
            "{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":2050,\"cvv\":123}",
            headers
        ),
        String.class
    );

    assertEquals(HttpStatus.OK, first.getStatusCode());
    assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    assertTrue(second.getBody().contains("Idempotency key already used for a different request"));
  }

  private boolean isBankSimulatorAvailable() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", 8080), 500);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
