package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler that maps application exceptions to structured HTTP error responses.
 */
@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  /**
   * Handles bean validation failures on request bodies, returning all violation messages
   * joined into a single string.
   *
   * @param ex the validation exception containing one or more constraint violations
   * @return 422 UNPROCESSABLE_ENTITY with a comma-separated list of violation messages
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
    String messages = ex.getBindingResult().getAllErrors().stream()
        .map(error -> error.getDefaultMessage())
        .collect(Collectors.joining(", "));
    LOG.warn("Payment rejected due to validation errors: {}", messages);
    return new ResponseEntity<>(new ErrorResponse(messages), HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /**
   * Handles {@link EventProcessingException}, returning the HTTP status carried by the exception.
   *
   * @param ex the exception describing the failure and its appropriate status
   * @return the response with the exception's status and message
   */
  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), ex.getStatus());
  }
}
