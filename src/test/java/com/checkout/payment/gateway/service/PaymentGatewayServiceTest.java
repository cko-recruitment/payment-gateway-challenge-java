package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private BankClient bankClient;

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    paymentGatewayService = new PaymentGatewayService(paymentRepository, bankClient);
  }

  @Test
  void shouldAuthorizePaymentWhenBankReturnsAuthorized() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "USD", 1050, "123");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-123");

    when(bankClient.processPayment(request)).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo(4242);
    assertThat(response.getCurrency()).isEqualTo("USD");
    assertThat(response.getAmount()).isEqualTo(1050);

    ArgumentCaptor<PostPaymentResponse> storedCaptor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentRepository).add(storedCaptor.capture());
    assertThat(storedCaptor.getValue().getId()).isEqualTo(response.getId());
  }

  @Test
  void shouldDeclinePaymentWhenBankReturnsDeclined() {
    PostPaymentRequest request = buildRequest("4242424242424240", 12, YearMonth.now().getYear() + 1,
        "GBP", 900, "123");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("");

    when(bankClient.processPayment(request)).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getCurrency()).isEqualTo("GBP");
  }

  @Test
  void shouldThrowPaymentValidationExceptionWhenCardNumberIsInvalid() {
    PostPaymentRequest request = buildRequest("4242424242424a", 12, YearMonth.now().getYear() + 1,
        "USD", 100, "123");

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Card number must only contain numeric characters");

    verify(paymentRepository, never()).add(any());
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldThrowPaymentValidationExceptionWhenCurrencyIsInvalid() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "840", 1500, "123");

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Currency must only contain letters");

    verify(paymentRepository, never()).add(any());
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldThrowPaymentValidationExceptionWhenMultipleValidationErrors() {
    PostPaymentRequest request = buildRequest("abc", 13, YearMonth.now().getYear() - 1,
        "US", -10, "12");

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Card number must only contain numeric characters")
        .hasMessageContaining("Expiry month must be between 1 and 12");

    verify(paymentRepository, never()).add(any());
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldPropagateBankProcessingException() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "USD", 100, "123");

    when(bankClient.processPayment(request))
        .thenThrow(new BankProcessingException("Connection timeout"));

    assertThatThrownBy(() -> paymentGatewayService.processPayment(request))
        .isInstanceOf(BankProcessingException.class)
        .hasMessageContaining("Connection timeout");
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
