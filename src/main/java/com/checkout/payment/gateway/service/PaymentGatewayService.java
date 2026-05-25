package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankSimulatorClient bankSimulatorClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      BankSimulatorClient bankSimulatorClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankSimulatorClient = bankSimulatorClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id)
        .orElseThrow(() -> new com.checkout.payment.gateway.exception.PaymentNotFoundException("Payment not found"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    List<String> validationErrors = validateRequest(paymentRequest);
    if (!validationErrors.isEmpty()) {
      LOG.warn("Payment request rejected: {}", validationErrors);
      throw new PaymentValidationException(String.join("; ", validationErrors));
    }

    BankPaymentResponse bankResponse = bankSimulatorClient.processPayment(paymentRequest);
    PaymentStatus status = bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;

    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(status);
    response.setCardNumberLastFour(Integer.parseInt(paymentRequest.getLastFourDigits()));
    response.setExpiryMonth(paymentRequest.getExpiryMonth());
    response.setExpiryYear(paymentRequest.getExpiryYear());
    response.setCurrency(paymentRequest.getCurrency().toUpperCase());
    response.setAmount(paymentRequest.getAmount());

    paymentsRepository.add(response);
    return response;
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
      } else if (!currency.matches("[A-Z]{3}")) {
        errors.add("Currency must only contain letters");
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
