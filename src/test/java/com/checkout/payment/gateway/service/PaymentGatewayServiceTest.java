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
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.security.SecureLogger;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private SecureLogger secureLogger;

  @Mock
  private IdempotencyRepository idempotencyRepository;

  @Mock
  private BankClient bankClient;

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    paymentGatewayService = new PaymentGatewayService(secureLogger, idempotencyRepository, bankClient);
  }

  @Test
  void shouldReturnPaymentByDedupeKeyWhenFound() {
    String merchantId = "merchant-123";
    String key = "dedupe-key-123";
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setStatus(PaymentStatus.AUTHORIZED);

    when(idempotencyRepository.getByKey(merchantId, key)).thenReturn(Optional.of(payment));

    Optional<PostPaymentResponse> result = paymentGatewayService.getPaymentByDedupeKey(merchantId, key);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void shouldReturnEmptyWhenDedupeKeyNotFound() {
    String merchantId = "merchant-123";
    String key = "non-existent";

    when(idempotencyRepository.getByKey(merchantId, key)).thenReturn(Optional.empty());

    Optional<PostPaymentResponse> result = paymentGatewayService.getPaymentByDedupeKey(merchantId, key);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnPaymentByIdWhenFound() {
    String merchantId = "merchant-123";
    UUID id = UUID.randomUUID();
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(id);
    payment.setStatus(PaymentStatus.AUTHORIZED);

    when(idempotencyRepository.getById(merchantId, id)).thenReturn(Optional.of(payment));

    Optional<PostPaymentResponse> result = paymentGatewayService.getPaymentById(merchantId, id);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(id);
  }

  @Test
  void shouldAuthorizePaymentWhenBankReturnsAuthorized() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "USD", 1050, "123");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-123");

    when(bankClient.processPayment(request)).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment("merchant-123", request, null);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo(4242);
    assertThat(response.getCurrency()).isEqualTo("USD");
    assertThat(response.getAmount()).isEqualTo(1050);
  }

  @Test
  void shouldSaveDedupeKeyWhenProvided() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "USD", 100, "123");
    String merchantId = "merchant-456";
    String dedupeKey = "dedupe-key-456";

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);

    when(bankClient.processPayment(request)).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(merchantId, request, dedupeKey);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    verify(idempotencyRepository).save(merchantId, dedupeKey, response);
  }

  @Test
  void shouldDeclinePaymentWhenBankReturnsDeclined() {
    PostPaymentRequest request = buildRequest("4242424242424240", 12, YearMonth.now().getYear() + 1,
        "GBP", 900, "123");

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("");

    when(bankClient.processPayment(request)).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment("merchant-123", request, null);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getCurrency()).isEqualTo("GBP");
  }

  @Test
  void shouldReturnRejectedResponseWhenCardNumberIsInvalid() {
    PostPaymentRequest request = buildRequest("4242424242424a", 12, YearMonth.now().getYear() + 1,
        "USD", 100, "123");

    PostPaymentResponse response = paymentGatewayService.processPayment("merchant-123", request, null);

    assertThat(response.getCode()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getDescription()).contains("Card number must only contain numeric characters");
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldReturnRejectedResponseWhenCurrencyIsInvalid() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "840", 1500, "123");

    PostPaymentResponse response = paymentGatewayService.processPayment("merchant-123", request, null);

    assertThat(response.getCode()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getDescription()).contains("Invalid ISO 4217 currency code");
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldReturnRejectedResponseWhenMultipleValidationErrors() {
    PostPaymentRequest request = buildRequest("abc", 13, YearMonth.now().getYear() - 1,
        "US", -10, "12");

    PostPaymentResponse response = paymentGatewayService.processPayment("merchant-123", request, null);

    assertThat(response.getCode()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getDescription()).contains("Card number must only contain numeric characters");
    assertThat(response.getDescription()).contains("Expiry month must be between 1 and 12");
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldPropagateBankProcessingException() {
    PostPaymentRequest request = buildRequest("4242424242424242", 12, YearMonth.now().getYear() + 1,
        "USD", 100, "123");

    when(bankClient.processPayment(request))
        .thenThrow(new BankProcessingException("Connection timeout"));

    assertThatThrownBy(() -> paymentGatewayService.processPayment("merchant-123", request, null))
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
