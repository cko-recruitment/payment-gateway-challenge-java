package com.checkout.payment.gateway.shared.exception;

import com.checkout.payment.gateway.generated.api.model.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@ControllerAdvice
public class GlobalControllerAdvice {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> e.getField() + ": " + e.getDefaultMessage())
        .findFirst()
        .orElse("Invalid request");
    log.warn("Validation failed for: {}", message);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(buildErrorResponse(message));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
    log.warn("Validation failed for: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(buildErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(buildErrorResponse("An unexpected error occurred"));
  }

  @ExceptionHandler(HttpClientErrorException.class)
  public ResponseEntity<ErrorResponse> handleHttpClientErrorException(HttpClientErrorException ex) {
    log.warn("Bank rejected request: {}", ex.getStatusCode());
    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .body(buildErrorResponse("Gateway rejected the request"));
  }

  private ErrorResponse buildErrorResponse(String message) {
    return ErrorResponse.builder().message(message).build();
  }
}
