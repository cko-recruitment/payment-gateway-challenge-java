package com.checkout.payment.gateway.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
  AUTHORIZED("Authorized"),
  DECLINED("Declined"),
  REJECTED("Rejected");

  private final String name;

  PaymentStatus(String name) {
    this.name = name;
  }

  @JsonValue
  public String getName() {
    return this.name;
  }
}
