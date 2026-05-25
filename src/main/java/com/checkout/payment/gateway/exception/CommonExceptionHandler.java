package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(PaymentGatewayException.class)
  public ResponseEntity<ErrorResponse> handlePaymentGatewayException(PaymentGatewayException ex) {
    LOG.error("Payment gateway error: [{}] {}", ex.getCode(), ex.getMessage(), ex);
    return new ResponseEntity<>(
        new ErrorResponse(ex.getCode(), ex.getMessage()),
        ex.getStatus()
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    LOG.error("Unexpected error occurred", ex);
    return new ResponseEntity<>(
        new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"),
        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
    );
  }
}
