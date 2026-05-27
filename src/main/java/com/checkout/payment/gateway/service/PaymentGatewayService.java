package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.GatewayCode;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.Link;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.security.SecureLogger;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

@Service
public class PaymentGatewayService {

  private final SecureLogger secureLogger;
  private final IdempotencyRepository idempotencyRepository;
  private final BankClient bankClient;

  public PaymentGatewayService(SecureLogger secureLogger,
      IdempotencyRepository idempotencyRepository, BankClient bankClient) {
    this.secureLogger = secureLogger;
    this.idempotencyRepository = idempotencyRepository;
    this.bankClient = bankClient;
  }

  public Optional<PostPaymentResponse> getPaymentByDedupeKey(String merchantId, String dedupeKey) {
    secureLogger.debug("Requesting payment by merchant={}, dedupeKey={}", merchantId, dedupeKey);
    return idempotencyRepository.getByKey(merchantId, dedupeKey);
  }

  public Optional<PostPaymentResponse> getPaymentById(String merchantId, UUID id) {
    secureLogger.debug("Requesting payment by merchant={}, id={}", merchantId, id);
    return idempotencyRepository.getById(merchantId, id);
  }

  public PostPaymentResponse processPayment(
      String merchantId, 
      PostPaymentRequest paymentRequest, 
      String dedupeKey) {
    secureLogger.info("Processing payment for merchant={}, request={}", merchantId, paymentRequest);
    List<String> validationErrors = validateRequest(paymentRequest);
    if (!validationErrors.isEmpty()) {
      secureLogger.warn("Payment request rejected for merchant={}: {}", merchantId, validationErrors);
      throw new PaymentValidationException(String.join("; ", validationErrors));
    }

    try {
      BankPaymentResponse bankResponse = bankClient.processPayment(paymentRequest);
      PaymentStatus status = bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;

      PostPaymentResponse response = new PostPaymentResponse();
      response.setId(UUID.randomUUID());
      response.setStatus(status);
      response.setCardNumberLastFour(Integer.parseInt(paymentRequest.getLastFourDigits()));
      response.setExpiryMonth(paymentRequest.getExpiryMonth());
      response.setExpiryYear(paymentRequest.getExpiryYear());
      response.setCurrency(paymentRequest.getCurrency().toUpperCase());
      response.setAmount(paymentRequest.getAmount());
      response.setGatewayCode(status == PaymentStatus.AUTHORIZED ? GatewayCode.SUCCESS : GatewayCode.DECLINED);

      if (dedupeKey != null && !dedupeKey.isBlank()) {
        idempotencyRepository.save(merchantId, dedupeKey, response);
      }
      secureLogger.info("Payment processed: merchant={}, id={}, status={}", merchantId, response.getId(), status);
      return response;

    } catch (HttpServerErrorException ex) {
      secureLogger.error("Bank error for merchant={}: {}", merchantId, ex.getStatusCode());
      throw new BankProcessingException("Bank error: " + ex.getStatusCode(), ex);
    }
  }

  private List<String> validateRequest(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    if (request.getCardNumber() == null || request.getCardNumber().isBlank()) {
      errors.add("Card number is required");
    } else {
      String cardNumber = request.getCardNumber().trim();
      if (cardNumber.length() < 14 || cardNumber.length() > 19) {
        errors.add("Card number must be between 14 and 19 digits");
      }
      if (!cardNumber.matches("\\d+")) {
        errors.add("Card number must only contain numeric characters");
      }
    }

    if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      errors.add("Expiry month must be between 1 and 12");
    }
    if (request.getExpiryYear() < 0) {
      errors.add("Expiry year is required");
    }
    if (request.getExpiryMonth() >= 1 && request.getExpiryMonth() <= 12 && request.getExpiryYear() >= 0) {
      YearMonth now = YearMonth.now();
      YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
      if (!expiry.isAfter(now)) {
        errors.add("Expiry date must be in the future");
      }
    }

    if (request.getCurrency() == null || request.getCurrency().isBlank()) {
      errors.add("Currency is required");
    } else {
      String currency = request.getCurrency().trim().toUpperCase();
      if (currency.length() != 3) {
        errors.add("Currency must be 3 characters long");
      } else {
        try {
          java.util.Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
          errors.add("Invalid ISO 4217 currency code");
        }
      }
    }

    if (request.getAmount() <= 0) {
      errors.add("Amount must be a positive integer");
    }

    if (request.getCvv() == null || request.getCvv().isBlank()) {
      errors.add("CVV is required");
    } else {
      String cvv = request.getCvv().trim();
      if (cvv.length() < 3 || cvv.length() > 4) {
        errors.add("CVV must be 3 or 4 characters long");
      }
      if (!cvv.matches("\\d+")) {
        errors.add("CVV must only contain numeric characters");
      }
    }

    return errors;
  }
}
