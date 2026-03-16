package com.checkout.payment.gateway.exception;

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

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RejectedPaymentResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    List<String> errorMessages = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .toList();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new RejectedPaymentResponse("Rejected", errorMessages));
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<RejectedPaymentResponse> handlePaymentRejected(PaymentRejectedException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new RejectedPaymentResponse("Rejected", ex.getMessages()));
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<String> handlePaymentNotFound(PaymentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<String> handleBankUnavailable(BankUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleUnexpected(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred");
  }
}
