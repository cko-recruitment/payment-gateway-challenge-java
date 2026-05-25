package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final RestTemplate restTemplate;
  private final String bankUrl;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      RestTemplate restTemplate,
      @Value("${bank.url}") String bankUrl) {
    this.paymentsRepository = paymentsRepository;
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id)
        .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    List<String> validationErrors = validateRequest(paymentRequest);
    if (!validationErrors.isEmpty()) {
      LOG.warn("Payment request rejected: {}", validationErrors);
      return PostPaymentResponse.rejected(String.join("; ", validationErrors));
    }

    BankPaymentResponse bankResponse = callBank(paymentRequest);

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

  private BankPaymentResponse callBank(PostPaymentRequest paymentRequest) {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("card_number", paymentRequest.getCardNumber());
    requestBody.put("expiry_date", paymentRequest.getExpiryDate());
    requestBody.put("currency", paymentRequest.getCurrency().toUpperCase());
    requestBody.put("amount", paymentRequest.getAmount());
    requestBody.put("cvv", paymentRequest.getCvv());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

    try {
      ResponseEntity<BankPaymentResponse> response = restTemplate.postForEntity(bankUrl, httpEntity, BankPaymentResponse.class);
      BankPaymentResponse body = response.getBody();
      if (body == null) {
        throw new BankProcessingException("Bank returned an empty response");
      }
      return body;
    } catch (HttpStatusCodeException ex) {
      String message = "Bank returned status " + ex.getStatusCode();
      LOG.error(message, ex);
      throw new BankProcessingException(message, ex);
    } catch (Exception ex) {
      LOG.error("Bank call failed", ex);
      throw new BankProcessingException("Cannot reach acquiring bank", ex);
    }
  }
}
