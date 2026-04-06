package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing payment processing and retrieval endpoints.
 */
@RestController("api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  /**
   * Constructs the controller with its required service dependency.
   *
   * @param paymentGatewayService the service handling payment logic
   */
  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  /**
   * Retrieves a previously processed payment by its ID.
   *
   * @param id the UUID of the payment
   * @return 200 OK with the payment details, or 404 if not found
   */
  @GetMapping("/payment/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  /**
   * Processes a new payment request through the bank.
   *
   * <p>Requires an {@code Idempotency-Key} header containing a valid UUID.
   * Duplicate requests with the same key return stored response to prevent re-charging.</p>
   *
   * @param request        the payment request body
   * @param idempotencyKey a client-supplied UUID to ensure exactly-once processing
   * @return 200 OK with the payment result, or 400 if the request or key is invalid
   */
  @PostMapping("/payment/")
  public Mono<ResponseEntity<?>> processPayment(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
                  value = """
                      {
                        "expiry_month": 12,
                        "expiry_year": 2028,
                        "currency": "EUR",
                        "amount": 1500,
                        "cvv": 123,
                        "card_number": "2222405343248877",
                        "card_number_last_four": "8877"
                      }
                      """
              )))
      @RequestBody @Valid PostPaymentRequest request,
      @Parameter(
          name = "Idempotency-Key",
          description = "Client-supplied UUID to ensure exactly-once processing",
          required = true,
          example = "a7f3d5e2-9c1b-4f8a-b6d0-2e4c8f1a3b5d"
      )
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return paymentGatewayService.processPayment(request, idempotencyKey)
        .map(response -> new ResponseEntity<>(response, HttpStatus.OK));
  }
}
