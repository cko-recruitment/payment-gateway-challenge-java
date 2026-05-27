package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.GatewayCode;
import com.checkout.payment.gateway.model.BasePaymentResponse;
import com.checkout.payment.gateway.security.SecureLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private final SecureLogger secureLogger;

  public CommonExceptionHandler(SecureLogger secureLogger) {
    this.secureLogger = secureLogger;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<BasePaymentResponse> handleGenericException(Exception ex) {
    secureLogger.error("Unexpected error occurred", ex);
    BasePaymentResponse response = new BasePaymentResponse();
    response.setGatewayCode(GatewayCode.INTERNAL_ERROR);
    response.setDescription("An unexpected error occurred");
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
