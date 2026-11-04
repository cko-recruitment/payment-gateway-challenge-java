package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class ErrorResponse extends ProblemDetail {

  PaymentStatus paymentStatus;
  List<FieldViolation> errors;

  public List<FieldViolation> getError() {
    return errors;
  }

  public PaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public void setError(List<FieldViolation> errors) {
    this.errors = errors;
  }

  public void setPaymentStatus(PaymentStatus paymentStatus) {
    this.paymentStatus = paymentStatus;
  }

  public record FieldViolation(String field, String message, Object rejectedValue) {

  }

  public static ErrorResponse validation(HttpStatus status, List<FieldViolation> errors) {
    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setStatus(status);
    errorResponse.setTitle("Validation failed");
    errorResponse.setDetail("One or more fields are invalid");
    errorResponse.setError(errors);
    errorResponse.setPaymentStatus(PaymentStatus.REJECTED);

    return errorResponse;
  }
  
  public static ErrorResponse malformed(HttpStatus status, String detail) {
    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setStatus(status);
    errorResponse.setTitle(status.is5xxServerError() ? "Request failed" : "Malformed request");
    errorResponse.setDetail(detail);
    errorResponse.setError(List.of());
    errorResponse.setPaymentStatus(PaymentStatus.REJECTED);

    return errorResponse;
  }
}
