package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.bank.AcquiringBankClient;
import com.checkout.payment.gateway.service.metrics.PaymentMetricsRecorder;
import com.checkout.payment.gateway.service.validation.DefaultPaymentRequestValidator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceUnitTest {

  @Mock
  private AcquiringBankClient acquiringBankClient;
  @Mock
  private PaymentMetricsRecorder paymentMetricsRecorder;

  private PaymentGatewayService service;
  private PaymentsRepository paymentsRepository;

  @BeforeEach
  void setUp() {
    paymentsRepository = new PaymentsRepository();
    service = new PaymentGatewayService(
        paymentsRepository,
        new IdempotencyRepository(),
        new DefaultPaymentRequestValidator(),
        acquiringBankClient,
        paymentMetricsRecorder
    );
  }

  @Test
  void whenRequestIsInvalidThenPaymentIsRejectedWithoutCallingBank() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234");

    PostPaymentResponse response = service.processPaymentDetails(request);

    assertEquals(PaymentStatus.REJECTED, response.getStatus());
    verifyNoInteractions(acquiringBankClient);
  }

  @Test
  void whenRequestIsValidThenAuthorizedPaymentIsPersisted() {
    when(acquiringBankClient.processPayment(any())).thenReturn(PaymentStatus.AUTHORIZED);

    PostPaymentResponse response = service.processPaymentDetails(validRequest());

    assertNotNull(response.getId());
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals(response.getId(), paymentsRepository.get(response.getId()).orElseThrow().getId());
  }

  @Test
  void whenSameIdempotencyKeyAndSamePayloadThenBankCalledOnceAndIdReused() {
    when(acquiringBankClient.processPayment(any())).thenReturn(PaymentStatus.AUTHORIZED);

    String idempotencyKey = "idem-" + UUID.randomUUID();
    PostPaymentRequest request = validRequest();
    PostPaymentResponse first = service.processPaymentDetails(request, idempotencyKey);
    PostPaymentResponse second = service.processPaymentDetails(request, idempotencyKey);

    assertEquals(first.getId(), second.getId());
    assertEquals(PaymentStatus.AUTHORIZED, second.getStatus());
    verify(acquiringBankClient, times(1)).processPayment(request);
  }

  @Test
  void whenSameIdempotencyKeyUsedWithDifferentPayloadThenConflictIsThrown() {
    when(acquiringBankClient.processPayment(any())).thenReturn(PaymentStatus.AUTHORIZED);

    String idempotencyKey = "idem-" + UUID.randomUUID();
    PostPaymentRequest firstRequest = validRequest();
    PostPaymentRequest secondRequest = validRequest();
    secondRequest.setAmount(2000);
    service.processPaymentDetails(firstRequest, idempotencyKey);

    assertThrows(IdempotencyConflictException.class,
        () -> service.processPaymentDetails(secondRequest, idempotencyKey));
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
