package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.NamingBase;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final NamingBase NAMING = (NamingBase) PropertyNamingStrategies.SNAKE_CASE;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    var errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new ErrorResponse.FieldViolation(
            NAMING.translate(fe.getField()),
            fe.getDefaultMessage(),
            maskIfSensitive(fe.getField(), fe.getRejectedValue())))
        .toList();
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ErrorResponse.validation(errors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    String detail = (ex.getCause() instanceof InvalidFormatException ife)
        ? "Invalid value for '%s'".formatted(
        ife.getPath().isEmpty() ? "body" : ife.getPath().get(0).getFieldName())
        : "Request body is not valid JSON";
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ErrorResponse.malformed(detail));
  }

  private Object maskIfSensitive(String field, Object value) {
    return switch (field) {
      case "cardNumber", "cvv" -> "***";
      default -> value;
    };
  }
}