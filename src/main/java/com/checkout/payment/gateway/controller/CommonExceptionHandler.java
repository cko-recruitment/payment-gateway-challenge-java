package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.ErrorResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class CommonExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
    String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    
    String globalErrors = ex.getBindingResult().getGlobalErrors().stream()
            .map(error -> error.getObjectName() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    
    String errors = fieldErrors + (globalErrors.isEmpty() ? "" : (fieldErrors.isEmpty() ? "" : ", ") + globalErrors);
    
    log.warn("Validation failed: {}", errors);
    return new ResponseEntity<>(new ErrorResponse(errors), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
    String message = "Invalid request payload: " + ex.getMostSpecificCause().getMessage();
    log.warn("Malformed JSON or invalid enum value: {}", message);
    return new ResponseEntity<>(new ErrorResponse("Invalid input format or value"), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleEventProcessingException(EventProcessingException ex) {
    log.error("Handling domain exception: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error("Unexpected system failure caught in global exception handler: ", ex);
    return new ResponseEntity<>(new ErrorResponse("Internal server error encountered"), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
