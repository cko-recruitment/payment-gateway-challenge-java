package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Payment does not exist"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<PaymentResponse> handleInvalid(
      MethodArgumentNotValidException ex) {
    LOG.info("Invalid Payment request received", ex);

    return getInvalidRequestError();
  }

  @ExceptionHandler(BankRejectedException.class)
  public ResponseEntity<?> handleDeclined() {
    return getInvalidRequestError();
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<?> handleBankDown() {
    return ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("BANK_UNAVAILABLE"));
  }

  @NonNull
  private static ResponseEntity<PaymentResponse> getInvalidRequestError() {
    PaymentResponse response = new PaymentResponse();
    response.setStatus(PaymentStatus.REJECTED);

    return ResponseEntity.badRequest().body(response);
  }
}
