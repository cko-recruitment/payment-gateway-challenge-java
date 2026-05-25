package com.checkout.payment.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentGatewayServiceIntegrationTest {
  private static final String CARDS_PREFIX = "123456712345";
  private static final String AUTHORIZE_CARD_NUMBER_LAST4 = "4321";
  private static final String DECLINE_CARD_NUMBER_LAST4 = "5432";
  private static final String ERROR_CARD_NUMBER_LAST4 = "3210";


  @Autowired
  private PaymentGatewayService service;

  @Test
  void whenBankAuthorizesThenPaymentStored() {
    PostPaymentRequest request = validRequest(AUTHORIZE_CARD_NUMBER_LAST4);

    UUID id = service.processPayment(request);

    PostPaymentResponse response = service.getPaymentById(id);

    assertThat(response.getStatus())
        .isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour())
        .isEqualTo(AUTHORIZE_CARD_NUMBER_LAST4);
  }

  @Test
  void whenBankDeclinesThenPaymentStored() {
    PostPaymentRequest request = validRequest(DECLINE_CARD_NUMBER_LAST4);

    UUID id = service.processPayment(request);

    PostPaymentResponse response = service.getPaymentById(id);

    assertThat(response.getStatus())
        .isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getCardNumberLastFour())
        .isEqualTo(DECLINE_CARD_NUMBER_LAST4);
  }

  @Test
  void whenBankFailsThenBankUnavailableIsThrown() {
    PostPaymentRequest request = validRequest(ERROR_CARD_NUMBER_LAST4);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void whenPaymentIsInvalidThenErrorIsThrown() {
    var invalidRequest = new PostPaymentRequest();

    assertThatThrownBy(() -> service.processPayment(invalidRequest))
        .isInstanceOf(BankUnavailableException.class);
  }

  private PostPaymentRequest validRequest(String cardLast4) {
    var request = new PostPaymentRequest();
    request.setCardNumber(CARDS_PREFIX + cardLast4);

    request.setExpiryMonth(12);
    request.setExpiryYear(2222);
    request.setCurrency("USD");
    request.setAmount(42);
    request.setCvv("007");

    return request;
  }
}
