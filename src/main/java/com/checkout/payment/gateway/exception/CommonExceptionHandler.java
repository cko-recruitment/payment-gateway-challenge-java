package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class CommonExceptionHandler {

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> notFound(EventProcessingException ex) {
    return ResponseEntity.status(404).body(new ErrorResponse("Payment not found", null, null));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new TreeMap<>();
    for (var error : ex.getBindingResult().getFieldErrors()) {
      String field = switch (error.getField()) {
        case "cardNumber" -> "card_number";
        case "expiryMonth" -> "expiry_month";
        case "expiryYear" -> "expiry_year";
        case "expiryDateInFuture" -> "expiry_date";
        default -> error.getField();
      };
      errors.putIfAbsent(field, error.getDefaultMessage());
    }
    for (var error : ex.getBindingResult().getGlobalErrors()) {
      errors.putIfAbsent("expiry_date", error.getDefaultMessage());
    }
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("Invalid request", PaymentStatus.REJECTED, errors));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ErrorResponse> malformed(Exception ex) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("Malformed request or identifier", PaymentStatus.REJECTED, null));
  }

  @ExceptionHandler(AcquirerException.class)
  public ResponseEntity<ErrorResponse> acquirer(AcquirerException ex) {
    return ResponseEntity.status(ex.isUnavailable() ? 503 : 502)
        .body(new ErrorResponse(ex.getMessage(), null, null));
  }
}
