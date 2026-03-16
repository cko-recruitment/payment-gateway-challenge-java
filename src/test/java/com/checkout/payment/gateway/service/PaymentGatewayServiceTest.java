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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  private PaymentRequest request;

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

    Payment savedPayment = new Payment(
        UUID.randomUUID(),
        PaymentStatus.AUTHORIZED,
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

    when(bankRequestMapper.map(request)).thenReturn(bankRequest);
    when(acquiringBankClient.authorize(bankRequest)).thenReturn(bankResponse);
    when(paymentRepository.add(any(Payment.class))).thenReturn(savedPayment);
    when(paymentResponseMapper.map(savedPayment)).thenReturn(expectedResponse);

    PaymentResponse actual = paymentGatewayService.processPayment(request);

    assertNotNull(actual);
    assertEquals(PaymentStatus.AUTHORIZED, actual.getStatus());

    verify(paymentBusinessValidator).validate(request);
    verify(acquiringBankClient).authorize(bankRequest);

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).add(captor.capture());
    Payment persisted = captor.getValue();

    assertEquals(PaymentStatus.AUTHORIZED, persisted.getStatus());
    assertEquals("**** **** **** 4241", persisted.getMaskedCardNumber());
    assertEquals("AUTH-123", persisted.getAuthorizationCode());
  }

  @Test
  void shouldProcessDeclinedPayment() {
    BankingClientRequest bankRequest = new BankingClientRequest();
    BankingClientResponse bankResponse = new BankingClientResponse();
    bankResponse.setAuthorized(false);

    Payment savedPayment = new Payment(
        UUID.randomUUID(),
        PaymentStatus.DECLINED,
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

    when(bankRequestMapper.map(request)).thenReturn(bankRequest);
    when(acquiringBankClient.authorize(bankRequest)).thenReturn(bankResponse);
    when(paymentRepository.add(any(Payment.class))).thenReturn(savedPayment);
    when(paymentResponseMapper.map(savedPayment)).thenReturn(expectedResponse);

    PaymentResponse actual = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.DECLINED, actual.getStatus());

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).add(captor.capture());
    Payment persisted = captor.getValue();

    assertEquals(PaymentStatus.DECLINED, persisted.getStatus());
    assertNull(persisted.getAuthorizationCode());
    assertEquals("**** **** **** 4242", persisted.getMaskedCardNumber());
  }

  @Test
  void shouldNotCallBankWhenValidationFails() {
    doThrow(new PaymentRejectedException(java.util.List.of("CVV is invalid")))
        .when(paymentBusinessValidator).validate(request);

    assertThrows(PaymentRejectedException.class, () -> paymentGatewayService.processPayment(request));

    verify(paymentBusinessValidator).validate(request);
    verifyNoInteractions(bankRequestMapper);
    verifyNoInteractions(acquiringBankClient);
    verifyNoInteractions(paymentRepository);
  }

  @Test
  void shouldReturnPaymentById() {
    UUID paymentId = UUID.randomUUID();
    Payment payment = new Payment(
        paymentId,
        PaymentStatus.AUTHORIZED,
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
