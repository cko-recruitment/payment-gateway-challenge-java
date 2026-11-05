package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.RequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
@RequestMapping("/payment")
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);

  private final RequestService requestService;

  public PaymentGatewayController(RequestService requestService) {
    this.requestService = requestService;
  }

  @GetMapping("/{id}")
  public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID id) {
    LOG.info("Retrieving payment {}", id);
    PaymentResponse payment = requestService.getPaymentById(id);
    LOG.info("Retrieved payment {}, status {}", id, payment.paymentStatus());
    return ResponseEntity.ok(payment);
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> postPayment(
      @RequestBody @Valid PostPaymentRequest postPaymentRequest) {
    LOG.info("Accepted a payment request for {} {}", postPaymentRequest.amount(),
        postPaymentRequest.currency());
    UUID id = requestService.processPayment(postPaymentRequest);
    PaymentResponse payment = requestService.getPaymentById(id);
    LOG.info("Payment {} finished with status {}", id, payment.paymentStatus());
    return ResponseEntity.ok(payment);
  }
}
