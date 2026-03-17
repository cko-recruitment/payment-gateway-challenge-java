package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.RejectedPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.List;

/**
 * Global exception handler for the payment gateway application.

 * This class provides centralized exception handling across all controllers
 * using Spring's {@link ControllerAdvice} mechanism. It ensures that exceptions
 * are translated into consistent and meaningful HTTP responses for API clients.

 * Responsibilities:
 *   Catch and handle application-specific exceptions
 *   Map exceptions to appropriate HTTP status codes
 *   Provide structured error responses for clients
 *   Log all exceptions for observability and debugging


 * Error Handling Strategy:
 *   Validation and business rule violations return HTTP 400 (Bad Request)
 *   Resource not found errors return HTTP 404 (Not Found)
 *   External system failures (e.g., bank unavailability) return HTTP 502 (Bad Gateway)
 *   Unhandled exceptions return HTTP 500 (Internal Server Error)

 * Consistency:
 * Wherever applicable, errors are returned in structured response models such as
 * {@link ErrorResponse} and {@link RejectedPaymentResponse}.
 */
@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("EventProcessingException happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RejectedPaymentResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    LOG.error("MethodArgumentNotValidException happened", ex);
    List<String> errorMessages = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .toList();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new RejectedPaymentResponse(PaymentStatus.REJECTED.getName(), errorMessages));
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<RejectedPaymentResponse> handlePaymentRejected(PaymentRejectedException ex) {
    LOG.error("PaymentRejectedException happened", ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new RejectedPaymentResponse(PaymentStatus.REJECTED.getName(), ex.getMessages()));
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<String> handlePaymentNotFound(PaymentNotFoundException ex) {
    LOG.error("PaymentNotFoundException happened", ex);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<String> handleBankUnavailable(BankUnavailableException ex) {
    LOG.error("BankUnavailableException happened", ex);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleUnexpected(Exception ex) {
    LOG.error("Exception happened (Generic Interceptor)", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred");
  }
}
