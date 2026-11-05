package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.ErrorResponse.FieldViolation;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.NamingBase;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final NamingBase NAMING = (NamingBase) PropertyNamingStrategies.SNAKE_CASE;
  private static final Set<String> SENSITIVE_FIELDS = Set.of("card_number", "cvv");
  private static final String MASK = "***";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    Stream<FieldViolation> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> {
          String field = NAMING.translate(fe.getField());
          return new ErrorResponse.FieldViolation(
              field,
              fe.getDefaultMessage(),
              safeRejectedValue(field, fe.getRejectedValue()));
        });

    Stream<FieldViolation> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
        .map(oe -> new ErrorResponse.FieldViolation(
            "request",
            oe.getDefaultMessage(),
            null));

    List<FieldViolation> violations = Stream.concat(fieldErrors, globalErrors).toList();
    // Only the field names are logged; a rejected value can be the PAN or the CVV.
    LOG.warn("Rejected a request: {} field(s) failed validation: {}", violations.size(),
        violations.stream().map(FieldViolation::field).toList());

    return problem(HttpStatus.BAD_REQUEST,
        ErrorResponse.validation(HttpStatus.BAD_REQUEST, violations));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    String detail = (ex.getCause() instanceof MismatchedInputException mie)
        ? "Invalid value for '%s'".formatted(fieldNameOf(mie))
        : "Request body is not valid JSON";
    LOG.warn("Rejected an unreadable request body: {}", detail);
    return problem(HttpStatus.BAD_REQUEST, ErrorResponse.malformed(HttpStatus.BAD_REQUEST, detail));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    LOG.warn("Rejected a request carrying an invalid value for '{}'", ex.getName());
    return problem(HttpStatus.BAD_REQUEST,
        ErrorResponse.malformed(HttpStatus.BAD_REQUEST,
            "Invalid value for '%s'".formatted(ex.getName())));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex) {
    LOG.warn("Rejected a request with the unsupported content type '{}'", ex.getContentType());
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.malformed(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Content type '%s' is not supported".formatted(ex.getContentType())));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    LOG.warn("Rejected a request using the unsupported method '{}'", ex.getMethod());
    return problem(HttpStatus.METHOD_NOT_ALLOWED, ErrorResponse.malformed(
        HttpStatus.METHOD_NOT_ALLOWED,
        "Method '%s' is not supported for this endpoint".formatted(ex.getMethod())));
  }

  /**
   * Returns the name of the field by parsing the path.
   */
  private static String fieldNameOf(MismatchedInputException mie) {
    if (mie.getPath().isEmpty() || mie.getPath().get(0).getFieldName() == null) {
      return "body";
    }
    return NAMING.translate(mie.getPath().get(0).getFieldName());
  }

  private ResponseEntity<ErrorResponse> problem(HttpStatus status, ErrorResponse body) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }

  private Object safeRejectedValue(String field, Object value) {
    return (SENSITIVE_FIELDS.contains(field)) ? MASK : value;
  }
}
