package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.Link;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.security.SecureLogger;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentGatewayController implements PaymentGatewayApi {

  private final SecureLogger secureLogger;
  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(SecureLogger secureLogger, PaymentGatewayService paymentGatewayService) {
    this.secureLogger = secureLogger;
    this.paymentGatewayService = paymentGatewayService;
  }

  @GetMapping("/payments/idempotency/{key}")
  public ResponseEntity<GetPaymentResponse> getPaymentByIdempotencyKey(
      @RequestHeader("Merchant-Id") String merchantId,
      @PathVariable("key") String key) {
    secureLogger.info("Retrieving payment for merchant={} by idempotency key={}", merchantId, key);
    Optional<PostPaymentResponse> existing = paymentGatewayService.getPaymentByDedupeKey(merchantId, key);
    return existing.map(response -> {
          GetPaymentResponse getResponse = GetPaymentResponse.from(response);
          getResponse.addLink(new Link("self", "/api/v1/payments/idempotency/" + key, "GET"));
          getResponse.addLink(new Link("payment", "/api/v1/payments/" + response.getId(), "GET"));
          return new ResponseEntity<>(getResponse, HttpStatus.OK);
        })
        .orElseGet(() -> new ResponseEntity<>(GetPaymentResponse.notFound(), HttpStatus.OK));
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<GetPaymentResponse> getPaymentById(
      @RequestHeader("Merchant-Id") String merchantId,
      @PathVariable("id") UUID id) {
    secureLogger.info("Retrieving payment for merchant={} by id={}", merchantId, id);
    Optional<PostPaymentResponse> existing = paymentGatewayService.getPaymentById(merchantId, id);
    return existing.map(response -> {
          GetPaymentResponse getResponse = GetPaymentResponse.from(response);
          getResponse.addLink(new Link("self", "/api/v1/payments/" + id, "GET"));
          return new ResponseEntity<>(getResponse, HttpStatus.OK);
        })
        .orElseGet(() -> new ResponseEntity<>(GetPaymentResponse.notFound(), HttpStatus.OK));
  }

  @PostMapping("/payments")
  public ResponseEntity<PostPaymentResponse> processPayment(
      @RequestBody PostPaymentRequest paymentRequest,
      @RequestHeader("Merchant-Id") String merchantId,
      @RequestHeader(name = "Idempotency-Key") String dedupeKey) {

    secureLogger.info("Processing payment for merchant={}, request={}, dedupeKey={}", merchantId, paymentRequest, dedupeKey);

    Optional<PostPaymentResponse> existing = paymentGatewayService.getPaymentByDedupeKey(merchantId, dedupeKey);
    if (existing.isPresent()) {
      secureLogger.info("Returning dedupe response for merchant={}, key={}", merchantId, dedupeKey);
      return new ResponseEntity<>(existing.get(), HttpStatus.OK);
    }

    try {
      PostPaymentResponse response = paymentGatewayService.processPayment(merchantId, paymentRequest, dedupeKey);
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (PaymentValidationException ex) {
      secureLogger.warn("Payment request rejected for merchant={}: {}", merchantId, ex.getMessage());
      return new ResponseEntity<>(PostPaymentResponse.rejected(ex.getMessage()), HttpStatus.OK);
    }
  }
}
