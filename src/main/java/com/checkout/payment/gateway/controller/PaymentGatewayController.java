package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.RequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
public class PaymentGatewayController {

  private final RequestService requestService;

  public PaymentGatewayController(RequestService requestService) {
    this.requestService = requestService;
  }

  /**
   * Returns a previously processed payment. The representation is the same one the POST returned
   * when the payment was created.
   */
  @GetMapping("/{id}")
  public ResponseEntity<PostPaymentResponse> getPaymentById(@PathVariable UUID id) {
    return ResponseEntity.ok(requestService.getPaymentById(id));
  }

  @PostMapping
  public ResponseEntity<PostPaymentResponse> postPayment(
      @RequestBody @Valid PostPaymentRequest postPaymentRequest) {
    UUID id = requestService.processPayment(postPaymentRequest);
    return ResponseEntity.ok(requestService.getPaymentById(id));
  }
}
