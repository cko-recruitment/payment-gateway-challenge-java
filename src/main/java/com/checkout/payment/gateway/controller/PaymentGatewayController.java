package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
@RequestMapping("/payments")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @PostMapping
  @Operation(summary = "Process a payment", description = "Returns an authorized or declined payment. No automatic retries.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Payment authorized or declined"),
      @ApiResponse(responseCode = "400", description = "Rejected request; acquirer not called"),
      @ApiResponse(responseCode = "502", description = "Invalid or unexpected acquirer response"),
      @ApiResponse(responseCode = "503", description = "Acquirer unavailable; outcome may be unknown")
  })
  public ResponseEntity<PostPaymentResponse> processPayment(
      @Valid @RequestBody PostPaymentRequest request) {
    PostPaymentResponse payment = paymentGatewayService.processPayment(request);
    return ResponseEntity.created(URI.create("/payments/" + payment.id())).body(payment);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Retrieve a payment")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Stored payment"),
      @ApiResponse(responseCode = "400", description = "Malformed identifier"),
      @ApiResponse(responseCode = "404", description = "Payment not found")
  })
  public ResponseEntity<PostPaymentResponse> getPaymentById(@PathVariable UUID id) {
    return ResponseEntity.ok(paymentGatewayService.getPaymentById(id));
  }
}
