package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
@RequestMapping("/payment")
@Tag(name = "Payments",
    description = "Process a card payment through the acquiring bank, and retrieve one already "
        + "processed.")
public class PaymentGatewayController {

  private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);

  private final RequestService requestService;

  public PaymentGatewayController(RequestService requestService) {
    this.requestService = requestService;
  }

  @Operation(
      summary = "Retrieve a payment",
      description = """
          Returns a payment the gateway has already processed, looked up by the `id` it was
          given in the response to `POST /payment`.

          The payment is returned exactly as it was recorded, including a `Declined` one. Card
          details are not stored in full, so only the last four digits of the card number come
          back. An ID the gateway has never issued, or one belonging to a request that never
          reached the bank, returns **404**.
          """)
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "The payment was found.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PaymentResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Authorized payment",
                      description = "The bank authorized this payment.",
                      value = ApiExamples.RESPONSE_AUTHORIZED),
                  @ExampleObject(
                      name = "Declined payment",
                      description = "The bank declined this payment. It is still recorded and "
                          + "retrievable.",
                      value = ApiExamples.RESPONSE_DECLINED)})),
      @ApiResponse(
          responseCode = "400",
          description = "The ID in the path is not a valid UUID.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Invalid ID", value = ApiExamples.ERROR_INVALID_ID))),
      @ApiResponse(
          responseCode = "404",
          description = "No payment is held under that ID.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Payment not found", value = ApiExamples.ERROR_NOT_FOUND))),
      @ApiResponse(
          responseCode = "500",
          description = "The gateway could not process the request.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Internal error", value = ApiExamples.ERROR_INTERNAL)))})
  @GetMapping("/{id}")
  public ResponseEntity<PaymentResponse> getPaymentById(
      @Parameter(
          description = "Gateway ID of the payment, as returned by `POST /payment`.",
          example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          required = true)
      @PathVariable UUID id) {
    LOG.info("Retrieving payment {}", id);
    PaymentResponse payment = requestService.getPaymentById(id);
    LOG.info("Retrieved payment {}, status {}", id, payment.paymentStatus());
    return ResponseEntity.ok(payment);
  }

  @Operation(
      summary = "Process a payment",
      description = """
          Validates a card payment, sends it to the acquiring bank and returns the bank's
          decision. The call is synchronous: by the time it returns, the payment has been
          attempted and stored under the `id` in the response.

          **A declined payment is a 200.** `payment_status` carries the bank's decision,
          `Authorized` or `Declined`; a 4xx or 5xx means the payment was never attempted or its
          outcome is unknown, and nothing is stored.

          The gateway validates the card number, expiry date, currency, amount and CVV before
          calling the bank. A request that fails validation is rejected with **400** and a list
          of the offending fields; the card number and CVV are masked to `***` in that list.
          """,
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "The card payment to process. Amounts are in the currency's minor unit.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PostPaymentRequest.class),
              examples = {
                  @ExampleObject(
                      name = "Payment the bank authorizes",
                      description = "£105.00 on a card the simulator authorizes (it ends in an "
                          + "odd digit).",
                      value = ApiExamples.REQUEST_AUTHORIZED),
                  @ExampleObject(
                      name = "Payment the bank declines",
                      description = "The same payment on a card the simulator declines (it ends "
                          + "in an even digit).",
                      value = ApiExamples.REQUEST_DECLINED)})))
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "The bank reached a decision. Check `payment_status` for which one.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PaymentResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Authorized",
                      description = "The bank authorized the payment.",
                      value = ApiExamples.RESPONSE_AUTHORIZED),
                  @ExampleObject(
                      name = "Declined",
                      description = "The bank declined the payment. The request itself was "
                          + "valid, so this is a 200.",
                      value = ApiExamples.RESPONSE_DECLINED)})),
      @ApiResponse(
          responseCode = "400",
          description = "The request failed validation, or its body could not be read. The "
              + "payment was not sent to the bank.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Field validation failed",
                      description = "One entry per offending field. Sensitive values are masked.",
                      value = ApiExamples.ERROR_VALIDATION),
                  @ExampleObject(
                      name = "Card has expired",
                      description = "Month and year are each valid but the date has passed, so "
                          + "the violation is reported against `request`.",
                      value = ApiExamples.ERROR_EXPIRED_CARD),
                  @ExampleObject(
                      name = "Body could not be read",
                      description = "The JSON is malformed, or a field holds the wrong type.",
                      value = ApiExamples.ERROR_MALFORMED)})),
      @ApiResponse(
          responseCode = "405",
          description = "The HTTP method is not supported on this path.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Method not allowed", value = ApiExamples.ERROR_METHOD_NOT_ALLOWED))),
      @ApiResponse(
          responseCode = "415",
          description = "The `Content-Type` is not `application/json`.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Unsupported content type", value = ApiExamples.ERROR_UNSUPPORTED_MEDIA_TYPE))),
      @ApiResponse(
          responseCode = "500",
          description = "The gateway could not process the request.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Internal error", value = ApiExamples.ERROR_INTERNAL))),
      @ApiResponse(
          responseCode = "502",
          description = "The bank rejected the gateway's request, or could not be reached. The "
              + "outcome of the payment is unknown.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = {
                  @ExampleObject(
                      name = "Bank rejected the request",
                      description = "The bank answered 4xx.",
                      value = ApiExamples.ERROR_BANK_REJECTED),
                  @ExampleObject(
                      name = "Bank could not be reached",
                      description = "The call failed, timed out, or the answer could not be "
                          + "read.",
                      value = ApiExamples.ERROR_BANK_UNREACHABLE)})),
      @ApiResponse(
          responseCode = "503",
          description = "The bank is unavailable, so no payment can be taken. Safe to retry.",
          content = @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(name = "Bank unavailable", value = ApiExamples.ERROR_BANK_UNAVAILABLE)))})
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
