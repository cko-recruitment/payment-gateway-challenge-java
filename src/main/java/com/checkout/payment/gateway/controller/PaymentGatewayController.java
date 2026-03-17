package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
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

/**
 * REST controller responsible for handling payment gateway operations.
 * This controller exposes endpoints to:
     - Process a new payment request
     - Retrieve payment details by PaymentId (UUID)
 * The controller enforces idempotency for payment processing using the
   Idempotency-Key header to prevent duplicate transactions in case of retries.
 * All endpoints are exposed under the base path <code>/api</code>.
 */
@RestController
@RequestMapping("/api")
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);
  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  /**
   * This endpoint accepts payment details and forwards them to the payment service
   * for authorization. The request must include an <b>Idempotency-Key</b> header,
   * which ensures that repeated requests with the same key do not result in duplicate payments
   * @param request the payment request containing card and transaction details
   * @param paymentId the idempotency key provided as a request header, expected to be a valid UUID
   * @return a {@link ResponseEntity} containing the processed {@link PaymentResponse}
      and HTTP status {@code 201 CREATED} if successful
   */
  @PostMapping("/payments")
  public ResponseEntity<PaymentResponse> processPayment(
      @Valid @RequestBody PaymentRequest request,
      @RequestHeader("Idempotency-Key") String paymentId
  ) {
    PaymentResponse response = paymentGatewayService.processPayment(UUID.fromString(paymentId), request);
    LOG.info("Payment successfully processed. TransactionID: {}, Status: {}", response.getId(), response.getStatus());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  /**
   * Retrieves payment details by transaction ID.
   * This endpoint returns the current state of a payment, including its status
   * and associated metadata.
   * @param id the unique identifier of the payment transaction
   * @return a {@link ResponseEntity} containing the {@link PaymentResponse}
   * and HTTP status {@code 200 OK} if found
   */

  @GetMapping("/payments/{id}")
  public ResponseEntity<PaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    LOG.info("Fetching payment event details for ID: {}", id);
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

}
