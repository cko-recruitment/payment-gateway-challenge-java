package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.Client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.BankingClientRequest;
import com.checkout.payment.gateway.model.BankingClientResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.utils.BankingClientRequestMapper;
import com.checkout.payment.gateway.utils.PaymentRequestFingerPrintGenerator;
import com.checkout.payment.gateway.utils.PaymentResponseMapper;
import com.checkout.payment.gateway.validator.PaymentBusinessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentGatewayServiceTest {
  @Mock
  private PaymentsRepository paymentRepository;

  @Mock
  private PaymentBusinessValidator paymentBusinessValidator;

  @Mock
  private BankingClientRequestMapper bankRequestMapper;

  @Mock
  private PaymentResponseMapper paymentResponseMapper;

  @Mock
  private AcquiringBankClient acquiringBankClient;

  @Mock
  private PaymentRequestFingerPrintGenerator paymentRequestFingerprintGenerator;

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  private PaymentRequest request;
  private final String validIdempotencyKey = "550e8400-e29b-41d4-a716-446655440000";
  private final UUID validPaymentId = UUID.fromString(validIdempotencyKey);

  @BeforeEach
  void setUp() {
    request = new PaymentRequest();
    request.setCardNumber("4242424242424241");
    request.setExpiryMonth(12);
    request.setExpiryYear(2029);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
  }

  @Test
  void shouldProcessAuthorizedPayment() {
    BankingClientRequest bankRequest = new BankingClientRequest();
    BankingClientResponse bankResponse = new BankingClientResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("AUTH-123");

    String fingerprint = "request-fingerprint-1";

    Payment savedPayment = new Payment(
        UUID.randomUUID(),
        PaymentStatus.AUTHORIZED,
        fingerprint,
        "4241",
        "USD",
        100,
        1,
        2029,
        "AUTH-123",
        Instant.now()
    );

    PaymentResponse expectedResponse = new PaymentResponse();
    expectedResponse.setStatus(PaymentStatus.AUTHORIZED);

    when(paymentRequestFingerprintGenerator.fingerprint(request)).thenReturn(fingerprint);
    when(paymentRepository.get(validPaymentId)).thenReturn(Optional.empty());
    when(bankRequestMapper.map(request)).thenReturn(bankRequest);
    when(acquiringBankClient.authorize(bankRequest)).thenReturn(bankResponse);
    when(paymentRepository.add(any(Payment.class))).thenReturn(savedPayment);
    when(paymentResponseMapper.map(savedPayment)).thenReturn(expectedResponse);

    PaymentResponse actual = paymentGatewayService.processPayment(validPaymentId, request);

    assertNotNull(actual);
    assertEquals(PaymentStatus.AUTHORIZED, actual.getStatus());

    verify(paymentBusinessValidator).validate(request);
    verify(acquiringBankClient).authorize(bankRequest);

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).add(captor.capture());
    Payment persisted = captor.getValue();

    assertEquals(validPaymentId, persisted.getId());
    assertEquals(PaymentStatus.AUTHORIZED, persisted.getStatus());
    assertEquals("**** **** **** 4241", persisted.getMaskedCardNumber());
    assertEquals("AUTH-123", persisted.getAuthorizationCode());
    assertEquals(fingerprint, persisted.getRequestFingerprint());
  }

  @Test
  void shouldProcessDeclinedPayment() {
    BankingClientRequest bankRequest = new BankingClientRequest();
    BankingClientResponse bankResponse = new BankingClientResponse();
    bankResponse.setAuthorized(false);

    String fingerprint = "request-fingerprint-2";

    Payment savedPayment = new Payment(
        validPaymentId,
        PaymentStatus.DECLINED,
        fingerprint,
        "4242",
        "USD",
        100,
        1,
        2029,
        null,
        Instant.now()
    );

    PaymentResponse expectedResponse = new PaymentResponse();
    expectedResponse.setStatus(PaymentStatus.DECLINED);

    request.setCardNumber("4242424242424242");

    when(paymentRequestFingerprintGenerator.fingerprint(request)).thenReturn(fingerprint);
    when(paymentRepository.get(validPaymentId)).thenReturn(Optional.empty());
    when(bankRequestMapper.map(request)).thenReturn(bankRequest);
    when(acquiringBankClient.authorize(bankRequest)).thenReturn(bankResponse);
    when(paymentRepository.add(any(Payment.class))).thenReturn(savedPayment);
    when(paymentResponseMapper.map(savedPayment)).thenReturn(expectedResponse);

    PaymentResponse actual = paymentGatewayService.processPayment(validPaymentId, request);

    assertEquals(PaymentStatus.DECLINED, actual.getStatus());

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).add(captor.capture());
    Payment persisted = captor.getValue();

    assertEquals(validPaymentId, persisted.getId());
    assertEquals(PaymentStatus.DECLINED, persisted.getStatus());
    assertNull(persisted.getAuthorizationCode());
    assertEquals("**** **** **** 4242", persisted.getMaskedCardNumber());
    assertEquals(fingerprint, persisted.getRequestFingerprint());
  }

  @Test
  void shouldReturnExistingPaymentForDuplicateRequestWithSamePayload() {
    String fingerprint = "same-fingerprint";

    Payment existingPayment = new Payment(
        validPaymentId,
        PaymentStatus.AUTHORIZED,
        fingerprint,
        "**** **** **** 4241",
        "USD",
        100,
        12,
        2029,
        "AUTH-123",
        Instant.now()
    );

    PaymentResponse expectedResponse = new PaymentResponse();
    expectedResponse.setStatus(PaymentStatus.AUTHORIZED);

    when(paymentRequestFingerprintGenerator.fingerprint(request)).thenReturn(fingerprint);
    when(paymentRepository.get(validPaymentId)).thenReturn(Optional.of(existingPayment));
    when(paymentResponseMapper.map(existingPayment)).thenReturn(expectedResponse);

    PaymentResponse actual = paymentGatewayService.processPayment(validPaymentId, request);

    assertNotNull(actual);
    assertEquals(PaymentStatus.AUTHORIZED, actual.getStatus());

    verify(paymentRepository).get(validPaymentId);
    verify(paymentResponseMapper).map(existingPayment);
    verify(paymentBusinessValidator, never()).validate(any());
    verifyNoInteractions(bankRequestMapper);
    verifyNoInteractions(acquiringBankClient);
    verify(paymentRepository, never()).add(any(Payment.class));
  }

  @Test
  void shouldRejectDuplicateRequestWhenPayloadIsDifferent() {
    String existingFingerprint = "existing-fingerprint";
    String newFingerprint = "new-fingerprint";

    Payment existingPayment = new Payment(
        validPaymentId,
        PaymentStatus.AUTHORIZED,
        existingFingerprint,
        "**** **** **** 4241",
        "USD",
        100,
        12,
        2029,
        "AUTH-123",
        Instant.now()
    );

    when(paymentRequestFingerprintGenerator.fingerprint(request)).thenReturn(newFingerprint);
    when(paymentRepository.get(validPaymentId)).thenReturn(Optional.of(existingPayment));

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> paymentGatewayService.processPayment(validPaymentId, request)
    );

    assertEquals(
        List.of("Idempotency key has already been used with a different payment request"),
        ex.getMessages()
    );

    verify(paymentRepository).get(validPaymentId);
    verify(paymentBusinessValidator, never()).validate(any());
    verifyNoInteractions(bankRequestMapper);
    verifyNoInteractions(acquiringBankClient);
    verify(paymentRepository, never()).add(any(Payment.class));
  }

  @Test
  void shouldNotCallBankWhenValidationFails() {
    String fingerprint = "request-fingerprint-3";

    when(paymentRequestFingerprintGenerator.fingerprint(request)).thenReturn(fingerprint);
    when(paymentRepository.get(validPaymentId)).thenReturn(Optional.empty());

    doThrow(new PaymentRejectedException(List.of("CVV is invalid")))
        .when(paymentBusinessValidator).validate(request);

    assertThrows(
        PaymentRejectedException.class,
        () -> paymentGatewayService.processPayment(validPaymentId, request)
    );

    verify(paymentBusinessValidator).validate(request);
    verifyNoInteractions(bankRequestMapper);
    verifyNoInteractions(acquiringBankClient);
    verify(paymentRepository, never()).add(any(Payment.class));
  }


  @Test
  void shouldReturnPaymentById() {
    UUID paymentId = UUID.randomUUID();
    Payment payment = new Payment(
        paymentId,
        PaymentStatus.AUTHORIZED,
        "fingerPrint",
        "4241",
        "USD",
        100,
        12,
        2029,
        "AUTH-123",
        Instant.now()
    );

    PaymentResponse expected = new PaymentResponse();
    expected.setStatus(PaymentStatus.AUTHORIZED);

    when(paymentRepository.get(paymentId)).thenReturn(Optional.of(payment));
    when(paymentResponseMapper.map(payment)).thenReturn(expected);

   PaymentResponse actual = paymentGatewayService.getPaymentById(paymentId);

    assertEquals(PaymentStatus.AUTHORIZED, actual.getStatus());
  }

  @Test
  void shouldThrowExceptionWhenPaymentNotFound() {
    UUID paymentId = UUID.randomUUID();
    when(paymentRepository.get(paymentId)).thenReturn(Optional.empty());

    assertThrows(PaymentNotFoundException.class, () -> paymentGatewayService.getPaymentById(paymentId));
  }

}
