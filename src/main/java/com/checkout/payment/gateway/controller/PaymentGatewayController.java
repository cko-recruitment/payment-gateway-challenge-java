package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import com.checkout.payment.gateway.utils.CardNumberMaskingUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/api")
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);
  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @PostMapping("/payments")
  public ResponseEntity<PaymentResponse> processPayment(
      @Valid @RequestBody PaymentRequest request,
      @RequestHeader("Idempotency-Key") String paymentId
  ) {
    PaymentResponse response = paymentGatewayService.processPayment(UUID.fromString(paymentId), request);
    LOG.info("Payment successfully processed. TransactionID: {}, Status: {}", response.getId(), response.getStatus());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<PaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    LOG.info("Fetching payment event details for ID: {}", id);
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

}
