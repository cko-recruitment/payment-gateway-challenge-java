package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
  
  /**
   * A missing payment is not a rejected one, so no payment status is reported.
   */
  public static ErrorResponse notFound(String detail) {
    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setStatus(HttpStatus.NOT_FOUND);
    errorResponse.setTitle("Payment not found");
    errorResponse.setDetail(detail);
    errorResponse.setError(List.of());

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
