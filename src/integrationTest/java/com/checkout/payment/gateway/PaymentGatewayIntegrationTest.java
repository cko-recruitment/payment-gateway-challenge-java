package com.checkout.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PaymentGatewayIntegrationTest {


  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void shouldProcessPaymentAndFetchById() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424241", "USD", 1050);

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getBody().getCardNumberLastFour()).isEqualTo(4241);

    String id = response.getBody().getId().toString();
    ResponseEntity<PostPaymentResponse> fetched = restTemplate.getForEntity(
        "/api/payment/{id}", PostPaymentResponse.class, id);

    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody()).isNotNull();
    assertThat(fetched.getBody().getId()).isEqualTo(response.getBody().getId());
  }

  @Test
  void shouldReturnDeclinedStatusForEvenCardNumber() {
    PostPaymentRequest request = buildPaymentRequest("4242424242424242", "GBP", 2500);

    ResponseEntity<PostPaymentResponse> response = restTemplate.postForEntity(
        "/api/payment", request, PostPaymentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getBody().getCurrency()).isEqualTo("GBP");
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
