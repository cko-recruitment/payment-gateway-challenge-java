package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BankExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(BankExceptionHandler.class);

  @ExceptionHandler(BankException.class)
  public ResponseEntity<ErrorResponse> handleBankFailure(BankException ex) {
    BankFailure failure = ex.failure();
    LOG.error("Call to the acquiring bank failed ({})", failure, ex);

    return ResponseEntity.status(failure.status())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ErrorResponse.malformed(failure.status(), failure.detail()));
  }
}
