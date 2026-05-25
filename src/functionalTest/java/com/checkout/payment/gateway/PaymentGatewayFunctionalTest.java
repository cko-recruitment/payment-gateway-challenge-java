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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
class PaymentGatewayFunctionalTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void shouldAuthorizePaymentWithOddCardNumber() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "USD", 1050);

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

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

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getBody().getId()).isNotNull();
    assertThat(response.getBody().getCurrency()).isEqualTo("GBP");
  }

  @Test
  void shouldRejectPaymentWithInvalidCardNumber() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424abc", "USD", 100);

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

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

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).isNotBlank();
  }

  @Test
  void shouldRejectPaymentWithInvalidCurrency() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424242", "840", 1500);

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).contains("Currency must only contain letters");
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

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getBody().getId()).isNull();
    assertThat(response.getBody().getMessage()).contains(";");
  }

  @Test
  void shouldRetrievePreviouslyCreatedPayment() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "EUR", 999);

    ResponseEntity<PostPaymentResponse> createResponse = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(createResponse.getBody()).isNotNull();
    UUID paymentId = createResponse.getBody().getId();

    ResponseEntity<PostPaymentResponse> getResponse = restTemplate.getForEntity(
        "/api/payment/{id}", PostPaymentResponse.class, paymentId);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).isNotNull();
    assertThat(getResponse.getBody().getId()).isEqualTo(paymentId);
    assertThat(getResponse.getBody().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(getResponse.getBody().getCurrency()).isEqualTo("EUR");
    assertThat(getResponse.getBody().getAmount()).isEqualTo(999);
  }

  @Test
  void shouldReturn404WhenPaymentNotFound() {
    UUID randomId = UUID.randomUUID();

    ResponseEntity<PostPaymentResponse> response = restTemplate.getForEntity(
        "/api/payment/{id}", PostPaymentResponse.class, randomId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
