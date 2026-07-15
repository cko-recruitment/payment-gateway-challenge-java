package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.model.IdempotentPaymentRecord;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyStore;
import com.checkout.payment.gateway.repository.PaymentStore;
import com.checkout.payment.gateway.service.bank.AcquiringBankClient;
import com.checkout.payment.gateway.service.metrics.PaymentMetricsRecorder;
import com.checkout.payment.gateway.service.validation.PaymentRequestValidator;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private static final int IDEMPOTENCY_LOCK_STRIPES = 64;
  private static final Object[] IDEMPOTENCY_LOCKS = createIdempotencyLocks();

  private final PaymentStore paymentStore;
  private final IdempotencyStore idempotencyStore;
  private final PaymentRequestValidator paymentRequestValidator;
  private final AcquiringBankClient acquiringBankClient;
  private final PaymentMetricsRecorder paymentMetricsRecorder;

  public PaymentGatewayService(PaymentStore paymentStore,
                               IdempotencyStore idempotencyStore,
                               PaymentRequestValidator paymentRequestValidator,
                               AcquiringBankClient acquiringBankClient,
                               PaymentMetricsRecorder paymentMetricsRecorder) {
    this.paymentStore = paymentStore;
    this.idempotencyStore = idempotencyStore;
    this.paymentRequestValidator = paymentRequestValidator;
    this.acquiringBankClient = acquiringBankClient;
    this.paymentMetricsRecorder = paymentMetricsRecorder;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentStore.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public UUID processPayment(PostPaymentRequest paymentRequest) {
    return processPaymentDetails(paymentRequest).getId();
  }

  public PostPaymentResponse processPaymentDetails(PostPaymentRequest paymentRequest) {
    return processPaymentDetails(paymentRequest, null);
  }

  public PostPaymentResponse processPaymentDetails(PostPaymentRequest paymentRequest, String idempotencyKey) {
    paymentMetricsRecorder.incrementTotalRequests();
    String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    LOG.info("Processing payment request. hasIdempotencyKey={}", normalizedIdempotencyKey != null);
    if (normalizedIdempotencyKey == null) {
      return processPaymentWithoutIdempotency(paymentRequest);
    }
    synchronized (lockForIdempotencyKey(normalizedIdempotencyKey)) {
      String requestSignature = buildRequestSignature(paymentRequest);
      return processPaymentWithIdempotency(paymentRequest, normalizedIdempotencyKey, requestSignature);
    }
  }

  private PostPaymentResponse processPaymentWithoutIdempotency(PostPaymentRequest paymentRequest) {
    if (!paymentRequestValidator.isValid(paymentRequest)) {
      paymentMetricsRecorder.incrementRejected();
      return buildPaymentResponse(
          null,
          PaymentStatus.REJECTED,
          safeLastFour(paymentRequest.getCardNumber()),
          paymentRequest.getExpiryMonth(),
          paymentRequest.getExpiryYear(),
          normalizeCurrency(paymentRequest.getCurrency()),
          paymentRequest.getAmount()
      );
    }

    PostPaymentResponse paymentResponse = callBankAndBuildResponse(paymentRequest);
    paymentStore.add(paymentResponse);
    LOG.info("Payment processed. status={}, paymentId={}", paymentResponse.getStatus(), paymentResponse.getId());
    return paymentResponse;
  }

  private PostPaymentResponse processPaymentWithIdempotency(PostPaymentRequest paymentRequest,
                                                            String normalizedIdempotencyKey,
                                                            String requestSignature) {
    IdempotentPaymentRecord existingRecord = idempotencyStore.get(normalizedIdempotencyKey).orElse(null);
    if (existingRecord != null) {
      if (!existingRecord.getRequestSignature().equals(requestSignature)) {
        paymentMetricsRecorder.incrementIdempotencyConflict();
        LOG.warn("Idempotency key conflict detected");
        throw new IdempotencyConflictException("Idempotency key already used for a different request");
      }
      paymentMetricsRecorder.incrementIdempotencyReplay();
      LOG.info("Returning stored response for idempotent replay");
      return existingRecord.getResponse();
    }

    if (!paymentRequestValidator.isValid(paymentRequest)) {
      paymentMetricsRecorder.incrementRejected();
      return buildPaymentResponse(
          null,
          PaymentStatus.REJECTED,
          safeLastFour(paymentRequest.getCardNumber()),
          paymentRequest.getExpiryMonth(),
          paymentRequest.getExpiryYear(),
          normalizeCurrency(paymentRequest.getCurrency()),
          paymentRequest.getAmount()
      );
    }

    PostPaymentResponse paymentResponse = callBankAndBuildResponse(paymentRequest);
    paymentStore.add(paymentResponse);

    IdempotentPaymentRecord concurrentRecord = idempotencyStore.addIfAbsent(
        normalizedIdempotencyKey,
        new IdempotentPaymentRecord(requestSignature, paymentResponse)
    );
    if (concurrentRecord != null) {
      if (!concurrentRecord.getRequestSignature().equals(requestSignature)) {
        paymentMetricsRecorder.incrementIdempotencyConflict();
        LOG.warn("Idempotency key conflict detected");
        throw new IdempotencyConflictException("Idempotency key already used for a different request");
      }
      paymentMetricsRecorder.incrementIdempotencyReplay();
      LOG.info("Returning stored response for idempotent replay");
      return concurrentRecord.getResponse();
    }
    LOG.info("Payment processed. status={}, paymentId={}", paymentResponse.getStatus(), paymentResponse.getId());
    return paymentResponse;
  }

  private PostPaymentResponse callBankAndBuildResponse(PostPaymentRequest paymentRequest) {
    try {
      PaymentStatus status = acquiringBankClient.processPayment(paymentRequest);
      if (status == PaymentStatus.AUTHORIZED) {
        paymentMetricsRecorder.incrementAuthorized();
      } else {
        paymentMetricsRecorder.incrementDeclined();
      }
      return buildPaymentResponse(
          UUID.randomUUID(),
          status,
          safeLastFour(paymentRequest.getCardNumber()),
          paymentRequest.getExpiryMonth(),
          paymentRequest.getExpiryYear(),
          normalizeCurrency(paymentRequest.getCurrency()),
          paymentRequest.getAmount()
      );
    } catch (BankUnavailableException ex) {
      paymentMetricsRecorder.incrementBankUnavailable();
      LOG.error("Bank simulator unavailable");
      throw new BankUnavailableException("Bank simulator is unavailable");
    }
  }

  private int safeLastFour(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < 4) {
      return 0;
    }
    return Integer.parseInt(cardNumber.substring(cardNumber.length() - 4));
  }

  private String normalizeCurrency(String currency) {
    if (currency == null) {
      return null;
    }
    return currency.trim().toUpperCase();
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null) {
      return null;
    }
    String normalized = idempotencyKey.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized;
  }

  private String buildRequestSignature(PostPaymentRequest paymentRequest) {
    return String.join("|",
        Objects.toString(paymentRequest.getCardNumber(), ""),
        String.valueOf(paymentRequest.getExpiryMonth()),
        String.valueOf(paymentRequest.getExpiryYear()),
        Objects.toString(normalizeCurrency(paymentRequest.getCurrency()), ""),
        String.valueOf(paymentRequest.getAmount()),
        String.valueOf(paymentRequest.getCvv())
    );
  }

  private PostPaymentResponse buildPaymentResponse(UUID id, PaymentStatus status, int cardNumberLastFour,
                                                   int expiryMonth, int expiryYear, String currency, int amount) {
    PostPaymentResponse paymentResponse = new PostPaymentResponse();
    paymentResponse.setId(id);
    paymentResponse.setStatus(status);
    paymentResponse.setCardNumberLastFour(cardNumberLastFour);
    paymentResponse.setExpiryMonth(expiryMonth);
    paymentResponse.setExpiryYear(expiryYear);
    paymentResponse.setCurrency(currency);
    paymentResponse.setAmount(amount);
    return paymentResponse;
  }

  private static Object[] createIdempotencyLocks() {
    Object[] locks = new Object[IDEMPOTENCY_LOCK_STRIPES];
    for (int i = 0; i < locks.length; i++) {
      locks[i] = new Object();
    }
    return locks;
  }

  private Object lockForIdempotencyKey(String key) {
    return IDEMPOTENCY_LOCKS[Math.floorMod(key.hashCode(), IDEMPOTENCY_LOCKS.length)];
  }
}
