package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.io.IOException;
import java.util.Properties;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private RestTemplate restTemplate;

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() throws IOException {
    Properties properties = new Properties();
    properties.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
    String bankUrl = properties.getProperty("bank.url");

    paymentGatewayService = new PaymentGatewayService(paymentsRepository, restTemplate, bankUrl);
  }

  @Test
  void shouldAuthorizePaymentWhenBankReturnsAuthorized() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "USD", 1050, "123");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-123");

    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo(4242);
    assertThat(response.getCurrency()).isEqualTo("USD");
    assertThat(response.getAmount()).isEqualTo(1050);

    ArgumentCaptor<PostPaymentResponse> storedCaptor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository).add(storedCaptor.capture());
    assertThat(storedCaptor.getValue().getId()).isEqualTo(response.getId());
  }

  @Test
  void shouldDeclinePaymentWhenBankReturnsDeclined() {
    PostPaymentRequest request = buildRequest("4242424242424240", 12, YearMonth.now().getYear() + 1,
        "GBP", 900, "123");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("");

    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getCurrency()).isEqualTo("GBP");
  }

  @Test
  void shouldReturnRejectedWhenCardNumberIsInvalid() {
    PostPaymentRequest request = buildRequest("4242424242424a", 12, YearMonth.now().getYear() + 1,
        "USD", 100, "123");

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getId()).isNull();
    assertThat(response.getMessage()).contains("Card number must only contain numeric characters");

    verify(paymentsRepository, never()).add(any());
    verify(restTemplate, never()).postForEntity(anyString(), any(), any());
  }

  @Test
  void shouldReturnRejectedWhenCurrencyIsInvalid() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "840", 1500, "123");

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getId()).isNull();
    assertThat(response.getMessage()).contains("Currency must only contain letters");

    verify(paymentsRepository, never()).add(any());
    verify(restTemplate, never()).postForEntity(anyString(), any(), any());
  }

  @Test
  void shouldReturnRejectedWhenMultipleValidationErrors() {
    PostPaymentRequest request = buildRequest("abc", 13, YearMonth.now().getYear() - 1,
        "US", -10, "12");

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getId()).isNull();
    assertThat(response.getMessage()).contains("Card number must only contain numeric characters");
    assertThat(response.getMessage()).contains("Expiry month must be between 1 and 12");

    verify(paymentsRepository, never()).add(any());
    verify(restTemplate, never()).postForEntity(anyString(), any(), any());
  }

  private PostPaymentRequest buildRequest(String cardNumber,
      int expiryMonth,
      int expiryYear,
      String currency,
      int amount,
      String cvv) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(expiryMonth);
    request.setExpiryYear(expiryYear);
    request.setCurrency(currency);
    request.setAmount(amount);
    request.setCvv(cvv);
    return request;
  }
}
