package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.IdempotentPaymentRecord;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP");

  private final PaymentsRepository paymentsRepository;
  private final IdempotencyRepository idempotencyRepository;
  private RestTemplate restTemplate;
  private String bankPaymentsUrl = "http://localhost:8080/payments";
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
                               IdempotencyRepository idempotencyRepository) {
    this.paymentsRepository = paymentsRepository;
    this.idempotencyRepository = idempotencyRepository;
  }

  @Autowired
  public void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Autowired(required = false)
  public void setMeterRegistry(MeterRegistry meterRegistry) {
    if (meterRegistry != null) {
      this.meterRegistry = meterRegistry;
    }
  }

  @Value("${bank.simulator.url:http://localhost:8080/payments}")
  public void setBankPaymentsUrl(String bankPaymentsUrl) {
    this.bankPaymentsUrl = bankPaymentsUrl;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public UUID processPayment(PostPaymentRequest paymentRequest) {
    return processPaymentDetails(paymentRequest).getId();
  }

  public PostPaymentResponse processPaymentDetails(PostPaymentRequest paymentRequest) {
    return processPaymentDetails(paymentRequest, null);
  }

  public PostPaymentResponse processPaymentDetails(PostPaymentRequest paymentRequest, String idempotencyKey) {
    incrementCounter("payment.requests.total");
    String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    LOG.info("Processing payment request. hasIdempotencyKey={}", normalizedIdempotencyKey != null);
    String requestSignature = buildRequestSignature(paymentRequest);
    if (normalizedIdempotencyKey != null) {
      IdempotentPaymentRecord existingRecord = idempotencyRepository.get(normalizedIdempotencyKey).orElse(null);
      if (existingRecord != null) {
        if (!existingRecord.getRequestSignature().equals(requestSignature)) {
          incrementCounter("payment.requests.idempotency_conflict");
          LOG.warn("Idempotency key conflict detected");
          throw new IdempotencyConflictException("Idempotency key already used for a different request");
        }
        incrementCounter("payment.requests.idempotency_replay");
        LOG.info("Returning stored response for idempotent replay");
        return existingRecord.getResponse();
      }
    }

    if (!isValidPaymentRequest(paymentRequest)) {
      incrementCounter("payment.requests.rejected");
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

    BankPaymentRequest bankPaymentRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        String.format("%02d/%d", paymentRequest.getExpiryMonth(), paymentRequest.getExpiryYear()),
        normalizeCurrency(paymentRequest.getCurrency()),
        paymentRequest.getAmount(),
        String.valueOf(paymentRequest.getCvv())
    );

    ResponseEntity<BankPaymentResponse> bankResponse;
    try {
      bankResponse = restTemplate.postForEntity(
          bankPaymentsUrl,
          bankPaymentRequest,
          BankPaymentResponse.class
      );
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode().is5xxServerError()) {
        incrementCounter("payment.requests.bank_unavailable");
        LOG.error("Bank simulator unavailable (status={})", ex.getStatusCode().value());
        throw new BankUnavailableException("Bank simulator is unavailable");
      }
      throw ex;
    } catch (ResourceAccessException ex) {
      incrementCounter("payment.requests.bank_unavailable");
      LOG.error("Bank simulator is unreachable");
      throw new BankUnavailableException("Bank simulator is unavailable");
    }

    PaymentStatus status = PaymentStatus.DECLINED;
    if (bankResponse.getBody() != null && bankResponse.getBody().isAuthorized()) {
      status = PaymentStatus.AUTHORIZED;
      incrementCounter("payment.requests.authorized");
    } else {
      incrementCounter("payment.requests.declined");
    }

    PostPaymentResponse paymentResponse = buildPaymentResponse(
        UUID.randomUUID(),
        status,
        safeLastFour(paymentRequest.getCardNumber()),
        paymentRequest.getExpiryMonth(),
        paymentRequest.getExpiryYear(),
        normalizeCurrency(paymentRequest.getCurrency()),
        paymentRequest.getAmount()
    );
    paymentsRepository.add(paymentResponse);

    if (normalizedIdempotencyKey != null) {
      idempotencyRepository.add(
          normalizedIdempotencyKey,
          new IdempotentPaymentRecord(requestSignature, paymentResponse)
      );
    }
    LOG.info("Payment processed. status={}, paymentId={}", paymentResponse.getStatus(), paymentResponse.getId());
    return paymentResponse;
  }

  private boolean isValidPaymentRequest(PostPaymentRequest paymentRequest) {
    if (paymentRequest.getCardNumber() == null || !paymentRequest.getCardNumber().matches("^\\d{14,19}$")) {
      return false;
    }
    if (paymentRequest.getExpiryMonth() < 1 || paymentRequest.getExpiryMonth() > 12) {
      return false;
    }
    LocalDate now = LocalDate.now();
    if (paymentRequest.getExpiryYear() < now.getYear()) {
      return false;
    }
    if (paymentRequest.getExpiryYear() == now.getYear()
        && paymentRequest.getExpiryMonth() <= now.getMonthValue()) {
      return false;
    }
    String currency = normalizeCurrency(paymentRequest.getCurrency());
    if (currency == null || currency.length() != 3 || !SUPPORTED_CURRENCIES.contains(currency)) {
      return false;
    }
    if (paymentRequest.getAmount() <= 0) {
      return false;
    }
    String cvv = String.valueOf(paymentRequest.getCvv());
    return cvv.matches("^\\d{3,4}$");
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

  private void incrementCounter(String metricName) {
    Counter.builder(metricName).register(meterRegistry).increment();
  }
}
