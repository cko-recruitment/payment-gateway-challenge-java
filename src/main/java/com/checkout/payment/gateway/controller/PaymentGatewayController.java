package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Timed(value = "payment.get", description = "Latency for retrieving payment by ID")
  @GetMapping("/payment/{paymentId}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID paymentId) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(paymentId), HttpStatus.OK);
  }

  @Timed(value = "payment.process", description = "Latency for processing new payments")
  @PostMapping("/payment")
  public ResponseEntity<PostPaymentResponse> processPayment(
          @RequestBody @Valid PostPaymentRequest request,
          @RequestHeader(value = "Idempotency-Key") UUID paymentId,
      @RequestHeader(value = "X-Merchant-Id") UUID merchantId) {
    PostPaymentResponse response = paymentGatewayService.processPayment(request, paymentId, merchantId);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }
}
