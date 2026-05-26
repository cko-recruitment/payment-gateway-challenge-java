package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Payment Gateway", description = "Payment processing and retrieval APIs")
@SecurityRequirement(name = "MerchantId")
public interface PaymentGatewayApi {

  @Operation(
      summary = "Retrieve payment by idempotency key",
      description = "Returns a previously processed payment using the merchant's idempotency key"
  )
  @ApiResponse(responseCode = "200", description = "Payment found",
      content = @Content(schema = @Schema(implementation = GetPaymentResponse.class),
          examples = @ExampleObject(name = "found", value = """
              {
                "code": "SUCCESS",
                "description": "Payment authorized successfully",
                "action": "none",
                "id": "7d474a2e-4748-463c-ad64-670045457a6e",
                "status": "Authorized",
                "cardNumberLastFour": 4241,
                "expiryMonth": 12,
                "expiryYear": 2027,
                "currency": "USD",
                "amount": 100,
                "links": [
                  {"rel": "self", "href": "/api/v1/payments/idempotency/dedupe-key-1", "method": "GET"},
                  {"rel": "payment", "href": "/api/v1/payments/7d474a2e-4748-463c-ad64-670045457a6e", "method": "GET"}
                ]
              }
              """)))
  @ApiResponse(responseCode = "200", description = "Payment not found",
      content = @Content(schema = @Schema(implementation = GetPaymentResponse.class),
          examples = @ExampleObject(name = "not_found", value = """
              {
                "code": "PAYMENT_NOT_FOUND",
                "description": "The requested payment could not be found",
                "action": "none"
              }
              """)))
  ResponseEntity<GetPaymentResponse> getPaymentByIdempotencyKey(
      @Parameter(description = "Merchant identifier", required = true)
      @RequestHeader("Merchant-Id") String merchantId,
      @Parameter(description = "Idempotency key from original request", required = true)
      @PathVariable("key") String key);

  @Operation(
      summary = "Retrieve payment by system ID",
      description = "Returns a payment using the system-generated UUID"
  )
  @ApiResponse(responseCode = "200", description = "Payment found or not found",
      content = @Content(schema = @Schema(implementation = GetPaymentResponse.class)))
  ResponseEntity<GetPaymentResponse> getPaymentById(
      @Parameter(description = "Merchant identifier", required = true)
      @RequestHeader("Merchant-Id") String merchantId,
      @Parameter(description = "System-generated payment UUID", required = true)
      @PathVariable("id") UUID id);

  @Operation(
      summary = "Process a new payment",
      description = "Validates, processes via bank, and stores the payment. Idempotent via Idempotency-Key header."
  )
  @ApiResponse(responseCode = "200", description = "Payment authorized",
      content = @Content(schema = @Schema(implementation = PostPaymentResponse.class),
          examples = @ExampleObject(name = "authorized", value = """
              {
                "code": "SUCCESS",
                "description": "Payment authorized successfully",
                "action": "none",
                "id": "7d474a2e-4748-463c-ad64-670045457a6e",
                "status": "Authorized",
                "cardNumberLastFour": 4241,
                "expiryMonth": 12,
                "expiryYear": 2027,
                "currency": "USD",
                "amount": 100,
                "links": [
                  {"rel": "self", "href": "/api/v1/payments/7d474a2e-4748-463c-ad64-670045457a6e", "method": "GET"},
                  {"rel": "idempotency", "href": "/api/v1/payments/idempotency/dedupe-key-1", "method": "GET"}
                ]
              }
              """)))
  @ApiResponse(responseCode = "200", description = "Payment declined by bank",
      content = @Content(schema = @Schema(implementation = PostPaymentResponse.class),
          examples = @ExampleObject(name = "declined", value = """
              {
                "code": "PAYMENT_DECLINED",
                "description": "Payment was declined by the bank",
                "action": "contact_support",
                "id": "8e585c0d-8b43-4cc5-9e68-1e5e2c6a1a2b",
                "status": "Declined",
                "cardNumberLastFour": 4242,
                "expiryMonth": 12,
                "expiryYear": 2027,
                "currency": "GBP",
                "amount": 2500,
                "links": [
                  {"rel": "self", "href": "/api/v1/payments", "method": "POST"}
                ]
              }
              """)))
  @ApiResponse(responseCode = "200", description = "Request validation failed",
      content = @Content(schema = @Schema(implementation = PostPaymentResponse.class),
          examples = @ExampleObject(name = "rejected", value = """
              {
                "code": "INVALID_REQUEST",
                "description": "Card number must only contain numeric characters; Invalid ISO 4217 currency code",
                "action": "fix_request",
                "id": null,
                "status": "Rejected",
                "links": [
                  {"rel": "self", "href": "/api/v1/payments", "method": "POST"}
                ]
              }
              """)))
  ResponseEntity<PostPaymentResponse> processPayment(
      @Parameter(description = "Payment request", required = true)
      @RequestBody PostPaymentRequest paymentRequest,
      @Parameter(description = "Merchant identifier", required = true)
      @RequestHeader("Merchant-Id") String merchantId,
      @Parameter(description = "Idempotency key for deduplication", required = true)
      @RequestHeader(name = "Idempotency-Key") String dedupeKey);
}
