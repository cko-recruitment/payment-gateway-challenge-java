package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.Client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.BankingClientResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.utils.BankingClientRequestMapper;
import com.checkout.payment.gateway.utils.CardNumberMaskingUtil;
import com.checkout.payment.gateway.utils.PaymentRequestFingerPrintGenerator;
import com.checkout.payment.gateway.utils.PaymentResponseMapper;
import com.checkout.payment.gateway.validator.PaymentBusinessValidator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Service responsible for orchestrating payment processing operations.
 * This service acts as the core business layer of the payment gateway and handles:
    - Processing new payment requests
    - Ensuring idempotency using a unique payment identifier
    - Interacting with the acquiring bank for authorization
    - Persisting and retrieving payment records

 * Idempotency
 * Each payment request is associated with a unique {@link UUID} (idempotency key).
 * If a request with the same key is received:
     - If the request payload matches (based on fingerprint), the existing payment is returned
     - If the payload differs, the request is rejected to prevent inconsistent state

 * Processing Flow:
     - Generate a fingerprint of the incoming request
     - Check if a payment with the given idempotency key already exists
     - If exists, validate fingerprint and return existing response
     - If not, validate request and send authorization request to acquiring bank
     - Persist the payment with the result and return response

 * This implementation ensures consistency, avoids duplicate transactions,
 * and encapsulates all business logic related to payment lifecycle management.
 */
@Service
public class PaymentGatewayService {

  private final PaymentsRepository paymentsRepository;
  private final AcquiringBankClient client;
  private final PaymentBusinessValidator validator;
  private final BankingClientRequestMapper bankingClientRequestMapper;
  private final PaymentResponseMapper paymentResponseMapper;
  private final PaymentRequestFingerPrintGenerator fingerPrintGenerator;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, AcquiringBankClient client,
      PaymentBusinessValidator validator, BankingClientRequestMapper bankingClientRequestMapper,
      PaymentResponseMapper paymentResponseMapper, PaymentRequestFingerPrintGenerator fingerPrintGenerator) {
    this.paymentsRepository = paymentsRepository;
    this.client = client;
    this.validator = validator;
    this.bankingClientRequestMapper = bankingClientRequestMapper;
    this.paymentResponseMapper = paymentResponseMapper;
    this.fingerPrintGenerator = fingerPrintGenerator;
  }

  public PaymentResponse processPayment(UUID paymentId, PaymentRequest request) {
    String fingerprint = fingerPrintGenerator.fingerprint(request);

    return paymentsRepository.get(paymentId)
        .map(existingPayment -> handleDuplicateRequest(existingPayment, fingerprint))
        .orElseGet(() -> createNewPayment(paymentId, request, fingerprint));
  }

  public PaymentResponse getPaymentById(UUID paymentId) {
    Payment payment = paymentsRepository.get(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException("Payment not found for id: " + paymentId));

    return paymentResponseMapper.map(payment);
  }

  private PaymentResponse handleDuplicateRequest(Payment existingPayment, String fingerprint) {
    if (!existingPayment.getRequestFingerprint().equals(fingerprint)) {
      throw new PaymentRejectedException(
          List.of("Idempotency key has already been used with a different payment request")
      );
    }

    return paymentResponseMapper.map(existingPayment);
  }

  private PaymentResponse createNewPayment(UUID paymentId,
      PaymentRequest request,
      String fingerprint) {
    validator.validate(request);

    BankingClientResponse bankResponse =
        client.authorize(bankingClientRequestMapper.map(request));

    Payment payment = buildPayment(paymentId, request, bankResponse, fingerprint);
    Payment savedPayment = paymentsRepository.add(payment);

    return paymentResponseMapper.map(savedPayment);
  }

  private Payment buildPayment(UUID paymentId,
      PaymentRequest request,
      BankingClientResponse bankResponse,
      String fingerprint) {
    PaymentStatus status = bankResponse.getAuthorized()
        ? PaymentStatus.AUTHORIZED
        : PaymentStatus.DECLINED;

    String authorizationCode = bankResponse.getAuthorized()
        ? bankResponse.getAuthorizationCode()
        : null;

    return new Payment(
        paymentId,
        status,
        fingerprint,
        CardNumberMaskingUtil.mask(request.getCardNumber()),
        request.getCurrency(),
        request.getAmount(),
        request.getExpiryMonth(),
        request.getExpiryYear(),
        authorizationCode,
        Instant.now()
    );
  }
}
