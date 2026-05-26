package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.security.SecureLogger;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class BankSimulatorClientTest {

  @Mock
  private SecureLogger secureLogger;

  @Mock
  private RestTemplate restTemplate;

  private BankSimulatorClient bankSimulatorClient;

  @BeforeEach
  void setUp() {
    bankSimulatorClient = new BankSimulatorClient(secureLogger, restTemplate, "http://localhost:8080/payments");
  }

  @Test
  void shouldReturnBankResponseWhenAuthorized() {
    PostPaymentRequest request = buildRequest("4242424242424241");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-code-123");

    when(restTemplate.postForEntity(anyString(), any(), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));

    BankPaymentResponse result = bankSimulatorClient.processPayment(request);

    assertThat(result.isAuthorized()).isTrue();
    assertThat(result.getAuthorizationCode()).isEqualTo("auth-code-123");
  }

  @Test
  void shouldReturnBankResponseWhenDeclined() {
    PostPaymentRequest request = buildRequest("4242424242424242");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("");

    when(restTemplate.postForEntity(anyString(), any(), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));

    BankPaymentResponse result = bankSimulatorClient.processPayment(request);

    assertThat(result.isAuthorized()).isFalse();
  }

  @Test
  void shouldThrowBankProcessingExceptionWhenResponseBodyIsNull() {
    PostPaymentRequest request = buildRequest("4242424242424241");

    when(restTemplate.postForEntity(anyString(), any(), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(null));

    assertThatThrownBy(() -> bankSimulatorClient.processPayment(request))
        .isInstanceOf(BankProcessingException.class)
        .hasMessageContaining("Bank returned an empty response");
  }

  @Test
  void shouldPropagateHttpStatusCodeException() {
    PostPaymentRequest request = buildRequest("4242424242424240");

    when(restTemplate.postForEntity(anyString(), any(), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> bankSimulatorClient.processPayment(request))
        .isInstanceOf(HttpServerErrorException.class);
  }

  @Test
  void shouldWrapExceptionAsBankProcessingExceptionWhenRestTemplateFails() {
    PostPaymentRequest request = buildRequest("4242424242424241");

    when(restTemplate.postForEntity(anyString(), any(), eq(BankPaymentResponse.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    assertThatThrownBy(() -> bankSimulatorClient.processPayment(request))
        .isInstanceOf(BankProcessingException.class)
        .hasMessageContaining("Cannot reach acquiring bank")
        .cause()
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Connection refused");
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
