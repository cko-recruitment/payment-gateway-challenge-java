package com.checkout.payment.gateway.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.bank.AcquiringBankClient;
import com.checkout.payment.gateway.service.metrics.PaymentMetricsRecorder;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import com.checkout.payment.gateway.service.validation.DefaultPaymentRequestValidator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayRegressionTest {

  @Mock
  private AcquiringBankClient acquiringBankClient;
  @Mock
  private PaymentMetricsRecorder paymentMetricsRecorder;

  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayService(
        new PaymentsRepository(),
        new IdempotencyRepository(),
        new DefaultPaymentRequestValidator(),
        acquiringBankClient,
        paymentMetricsRecorder
    );
  }

  @Test
  void rejectedRequestMustNotConsumeIdempotencyKey() {
    PostPaymentRequest invalid = validRequest();
    invalid.setCardNumber("12");
    String key = "idem-" + UUID.randomUUID();

    PostPaymentResponse rejected = service.processPaymentDetails(invalid, key);
    assertEquals(PaymentStatus.REJECTED, rejected.getStatus());

    when(acquiringBankClient.processPayment(any())).thenReturn(PaymentStatus.AUTHORIZED);
    PostPaymentResponse accepted = service.processPaymentDetails(validRequest(), key);

    assertEquals(PaymentStatus.AUTHORIZED, accepted.getStatus());
    assertNotNull(accepted.getId());
  }

  @Test
  void idempotencyKeyWithSurroundingSpacesMustReplayOriginal() {
    when(acquiringBankClient.processPayment(any())).thenReturn(PaymentStatus.AUTHORIZED);

    PostPaymentRequest request = validRequest();
    PostPaymentResponse first = service.processPaymentDetails(request, "key-123");
    PostPaymentResponse second = service.processPaymentDetails(request, "  key-123  ");

    assertEquals(first.getId(), second.getId());
    verify(acquiringBankClient, times(1)).processPayment(request);
  }

  @Test
  void idempotencySignatureMustTreatCurrencyCaseInsensitively() {
    when(acquiringBankClient.processPayment(any())).thenReturn(PaymentStatus.AUTHORIZED);

    String key = "idem-" + UUID.randomUUID();
    PostPaymentRequest upperCase = validRequest();
    PostPaymentRequest lowerCase = validRequest();
    lowerCase.setCurrency("usd");

    PostPaymentResponse first = service.processPaymentDetails(upperCase, key);
    PostPaymentResponse second = service.processPaymentDetails(lowerCase, key);

    assertEquals(first.getId(), second.getId());
    verify(acquiringBankClient, times(1)).processPayment(any());
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2099);
    request.setCurrency("USD");
    request.setAmount(1050);
    request.setCvv(123);
    return request;
  }
}
