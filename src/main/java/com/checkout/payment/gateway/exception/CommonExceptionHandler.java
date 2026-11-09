package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
    LOG.info("No payment held for ID {}", ex.getId());
    return problem(HttpStatus.NOT_FOUND,
        ErrorResponse.notFound("No payment found with ID %s".formatted(ex.getId())));
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Returning 500, the request could not be processed", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorResponse.malformed(HttpStatus.INTERNAL_SERVER_ERROR,
            "The request could not be processed"));
  }

  private ResponseEntity<ErrorResponse> problem(HttpStatus status, ErrorResponse body) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }
}
