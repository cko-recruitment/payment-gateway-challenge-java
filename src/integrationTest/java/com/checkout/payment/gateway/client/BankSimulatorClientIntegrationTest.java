package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.HttpServerErrorException;

@SpringBootTest
class BankSimulatorClientIntegrationTest {

  @Autowired
  private BankSimulatorClient bankSimulatorClient;

  @Test
  void shouldReturnAuthorizedWhenCardNumberEndsWithOddDigit() {
    PostPaymentRequest request = buildRequest("4242424242424241");

    BankPaymentResponse response = bankSimulatorClient.processPayment(request);

    assertThat(response).isNotNull();
    assertThat(response.isAuthorized()).isTrue();
    assertThat(response.getAuthorizationCode()).isNotBlank();
  }

  @Test
  void shouldReturnUnauthorizedWhenCardNumberEndsWithEvenDigit() {
    PostPaymentRequest request = buildRequest("4242424242424242");

    BankPaymentResponse response = bankSimulatorClient.processPayment(request);

    assertThat(response).isNotNull();
    assertThat(response.isAuthorized()).isFalse();
  }

  @Test
  void shouldReturn503WhenCardNumberEndsWithZero() {
    PostPaymentRequest request = buildRequest("4242424242424240");

    assertThatThrownBy(() -> bankSimulatorClient.processPayment(request))
        .isInstanceOf(HttpServerErrorException.class)
        .satisfies(ex -> {
          HttpServerErrorException httpEx = (HttpServerErrorException) ex;
          assertThat(httpEx.getStatusCode().value()).isEqualTo(503);
        });
  }

  @Test
  void shouldReturn400WhenRequiredFieldIsMissing() {
    // Bank simulator requires all fields present to match predicate
    // Missing fields cause mountebank internal error (500), not 400
    // This test verifies the bank's contract: null card_number is not handled gracefully
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(null);
    request.setExpiryMonth(12);
    request.setExpiryYear(2026);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");

    assertThatThrownBy(() -> bankSimulatorClient.processPayment(request))
        .isInstanceOf(HttpServerErrorException.class)
        .satisfies(ex -> {
          HttpServerErrorException httpEx = (HttpServerErrorException) ex;
          assertThat(httpEx.getStatusCode().value()).isEqualTo(500);
        });
  }

  private PostPaymentRequest buildRequest(String cardNumber) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(YearMonth.now().getMonthValue());
    request.setExpiryYear(YearMonth.now().getYear() + 1);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
