package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Runtime exception thrown when a payment-related operation fails.
 *
 * <p>Carries an {@link HttpStatus} so the exception handler can return the correct HTTP status
 * code without needing to inspect the message string.</p>
 */
public class EventProcessingException extends RuntimeException {

  private final HttpStatus status;

  /**
   * Constructs the exception with a descriptive message and the appropriate HTTP status.
   *
   * @param message human-readable description of the failure
   * @param status  the HTTP status code to return to the caller
   */
  public EventProcessingException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }

  /**
   * Returns the HTTP status associated with this exception.
   *
   * @return the HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }
}
