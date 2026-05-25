package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.security.SecureLogger;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;

@RestController
@RequestMapping("/api")
public class PaymentGatewayController {

  private final SecureLogger secureLogger;
  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(SecureLogger secureLogger, PaymentGatewayService paymentGatewayService) {
    this.secureLogger = secureLogger;
    this.paymentGatewayService = paymentGatewayService;
  }
  
  @GetMapping("/payment")
  public ResponseEntity<PostPaymentResponse> getPayment(
       @RequestHeader("Merchant-Id") String merchantId,
       @RequestHeader(name = "Idempotency-Key", required = false) String dedupeKey) {
    secureLogger.info("Retrieving payment for merchant={}, dedupeKey={}", merchantId, dedupeKey);
    if (dedupeKey == null || dedupeKey.isBlank()) {
      secureLogger.warn("Missing Idempotency-Key header for merchant={}", merchantId);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    } 
    Optional<PostPaymentResponse> existing = paymentGatewayService.getPaymentByDedupeKey(merchantId, dedupeKey);
    if (existing.isPresent()) {
      secureLogger.info("Payment found for merchant={}, key={}", merchantId, dedupeKey);
      return new ResponseEntity<>(existing.get(), HttpStatus.OK);
    } else {
      secureLogger.info("No payment found for merchant={}, key={}", merchantId, dedupeKey);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @PostMapping("/payment")
  public ResponseEntity<PostPaymentResponse> processPayment(
       @RequestBody PostPaymentRequest paymentRequest,
       @RequestHeader("Merchant-Id") String merchantId,
       @RequestHeader(name = "Idempotency-Key", required = false) String dedupeKey) {
    secureLogger.info("Processing payment for merchant={}, request={}, dedupeKey={}", merchantId, paymentRequest, dedupeKey);

    if (dedupeKey != null && !dedupeKey.isBlank()) {
      Optional<PostPaymentResponse> existing = paymentGatewayService.getPaymentByDedupeKey(merchantId, dedupeKey);
      if (existing.isPresent()) {
        secureLogger.info("Returning dedupe response for merchant={}, key={}", merchantId, dedupeKey);
        return new ResponseEntity<>(existing.get(), HttpStatus.OK);
      }
    }

    try {
      PostPaymentResponse response = paymentGatewayService.processPayment(merchantId, paymentRequest, dedupeKey);
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (PaymentValidationException ex) {
      secureLogger.warn("Payment request rejected for merchant={}: {}", merchantId, ex.getMessage());
      return new ResponseEntity<>(PostPaymentResponse.rejected(ex.getMessage()), HttpStatus.OK);
    } catch (HttpServerErrorException ex) {
      if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        secureLogger.error("Bank returned 503 Service Unavailable for merchant={}", merchantId, ex);
        return new ResponseEntity<>(PostPaymentResponse.declined("Bank service temporarily unavailable"), HttpStatus.OK);
      }
      secureLogger.error("Bank returned error for merchant={}: {}", merchantId, ex.getStatusCode());
      return new ResponseEntity<>(PostPaymentResponse.declined("Bank error: " + ex.getStatusCode()), HttpStatus.OK);
    } catch (BankProcessingException ex) {
      secureLogger.error("Bank processing failed for merchant={}", merchantId, ex);
      return new ResponseEntity<>(PostPaymentResponse.declined("Bank processing failed: " + ex.getMessage()), HttpStatus.OK);
    }
  }
}
