package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The ways a call to the acquiring bank can fail.
 */
public enum BankFailure {

  /**
   * The bank answered 4xx. We return 502 because the upstream bank returned an invalid response.
   */
  BANK_REJECTED_REQUEST(HttpStatus.BAD_GATEWAY,
      "The acquiring bank rejected the request from this gateway."),

  /**
   * The bank answered 5xx. We return 503 because we cannot process payments in this case.
   */
  BANK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
      "The acquiring bank is currently unavailable."),

  /**
   * No usable answer: the bank could not be reached, the call timed out, or the response could not
   * be parsed or lacked an authorisation decision.
   */
  BANK_UNREACHABLE(HttpStatus.BAD_GATEWAY,
      "The acquiring bank could not be reached.");

  private final HttpStatus status;
  private final String detail;

  BankFailure(HttpStatus status, String detail) {
    this.status = status;
    this.detail = detail;
  }

  public static BankFailure ofBankStatus(HttpStatusCode status) {
    return status.is5xxServerError() ? BANK_UNAVAILABLE : BANK_REJECTED_REQUEST;
  }

  public HttpStatus status() {
    return status;
  }

  public String detail() {
    return detail;
  }
}
