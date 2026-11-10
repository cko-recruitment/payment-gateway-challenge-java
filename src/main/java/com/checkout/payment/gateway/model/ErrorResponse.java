package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "ErrorResponse",
    description = """
        An RFC 9457 problem detail, returned as `application/problem+json` whenever a request
        could not be processed. `title` names the kind of failure, `detail` describes this
        occurrence, and `error` lists the offending fields when the failure was a validation
        one.""",
    example = """
        {
          "type": "about:blank",
          "title": "Validation failed",
          "status": 400,
          "detail": "One or more fields are invalid",
          "payment_status": "Rejected",
          "error": [
            {
              "field": "card_number",
              "message": "must contain digits only",
              "rejected_value": "***"
            }
          ]
        }""")
public class ErrorResponse extends ProblemDetail {

  PaymentStatus paymentStatus;
  List<FieldViolation> errors;

  @Schema(
      description = "The fields that failed validation, one entry each. Empty when the failure "
          + "was not about a specific field.")
  public List<FieldViolation> getError() {
    return errors;
  }

  @Schema(
      description = "Always `Rejected` when the gateway refused the payment. Absent when no "
          + "payment was in play, as on a 404.",
      nullable = true)
  public PaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public void setError(List<FieldViolation> errors) {
    this.errors = errors;
  }

  public void setPaymentStatus(PaymentStatus paymentStatus) {
    this.paymentStatus = paymentStatus;
  }

  @Schema(
      name = "FieldViolation",
      description = "One field that failed validation.")
  public record FieldViolation(
      @Schema(
          description = "The offending field, named as it appears in the request body. "
              + "`request` means the violation spans more than one field, as an expired card "
              + "does.",
          example = "card_number")
      String field,

      @Schema(
          description = "What is wrong with the value.",
          example = "must contain digits only")
      String message,

      @Schema(
          description = "The value that was rejected. Masked to `***` for the card number and "
              + "the CVV, and absent for a violation that spans fields.",
          example = "***",
          nullable = true)
      Object rejectedValue) {

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
