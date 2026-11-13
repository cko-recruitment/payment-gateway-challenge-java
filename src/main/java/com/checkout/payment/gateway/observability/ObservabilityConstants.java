package com.checkout.payment.gateway.observability;

public final class ObservabilityConstants {

  public static final String AUTHORIZED = "authorized";
  public static final String DECLINED = "declined";
  public static final String REQUEST_ID_MDC_KEY = "requestId";
  public static final String REQUEST_ID_HEADER = "X-Request-ID";

  private ObservabilityConstants() {
  }
}
