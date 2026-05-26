package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.enums.GatewayCode;
import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.Link;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.security.SecureLogger;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.client.HttpServerErrorException;

@Tag(name = "Payment Gateway", description = "Payment processing and retrieval APIs")
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "MerchantId")
public class PaymentGatewayController {

  private final SecureLogger secureLogger;
  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(SecureLogger secureLogger, PaymentGatewayService paymentGatewayService) {
    this.secureLogger = secureLogger;
    this.paymentGatewayService = paymentGatewayService;
  }

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
  @GetMapping("/payments/idempotency/{key}")
  public ResponseEntity<GetPaymentResponse> getPaymentByIdempotencyKey(
      @Parameter(description = "Merchant identifier", required = true)
      @RequestHeader("Merchant-Id") String merchantId,
      @Parameter(description = "Idempotency key from original request", required = true)
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

  @Operation(
      summary = "Retrieve payment by system ID",
      description = "Returns a payment using the system-generated UUID"
  )
  @ApiResponse(responseCode = "200", description = "Payment found or not found",
      content = @Content(schema = @Schema(implementation = GetPaymentResponse.class)))
  @GetMapping("/payments/{id}")
  public ResponseEntity<GetPaymentResponse> getPaymentById(
      @Parameter(description = "Merchant identifier", required = true)
      @RequestHeader("Merchant-Id") String merchantId,
      @Parameter(description = "System-generated payment UUID", required = true)
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
  @PostMapping("/payments")
  public ResponseEntity<PostPaymentResponse> processPayment(
      @Parameter(description = "Payment request", required = true)
      @RequestBody PostPaymentRequest paymentRequest,
      @Parameter(description = "Merchant identifier", required = true)
      @RequestHeader("Merchant-Id") String merchantId,
      @Parameter(description = "Idempotency key for deduplication", required = true)
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
    } catch (HttpServerErrorException ex) {
      PostPaymentResponse response = PostPaymentResponse.declined("");
      if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        secureLogger.error("Bank returned 503 Service Unavailable for merchant={}", merchantId, ex);
        response.setDescription("Bank service temporarily unavailable");
        response.setGatewayCode(GatewayCode.BANK_UNAVAILABLE);
      } else if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
        secureLogger.error("Bank returned 400 Bad Request for merchant={}", merchantId, ex);
        response.setDescription("Bank validation error");
        response.setGatewayCode(GatewayCode.BANK_VALIDATION_ERROR);
      } else {
        secureLogger.error("Bank returned error for merchant={}: {}", merchantId, ex.getStatusCode());
        response.setDescription("Bank error: " + ex.getStatusCode());
        response.setGatewayCode(GatewayCode.BANK_ERROR);
      }
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (BankProcessingException ex) {
      secureLogger.error("Bank processing failed for merchant={}", merchantId, ex);
      PostPaymentResponse response = PostPaymentResponse.declined("Bank processing failed: " + ex.getMessage());
      response.setGatewayCode(GatewayCode.BANK_ERROR);
      return new ResponseEntity<>(response, HttpStatus.OK);
    }
  }
}
