package com.checkout.payment.gateway.exception;

public class AcquirerException extends RuntimeException {

  private final boolean unavailable;

  public AcquirerException(boolean unavailable) {
    super(
        unavailable ? "Acquirer unavailable; payment outcome may be unknown"
            : "Invalid acquirer response");
    this.unavailable = unavailable;
  }

  public boolean isUnavailable() {
    return unavailable;
  }
}
