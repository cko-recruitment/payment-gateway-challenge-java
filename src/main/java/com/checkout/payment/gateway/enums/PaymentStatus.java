package com.checkout.payment.gateway.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "PaymentStatus",
    description = """
        What became of a payment.

        - `Authorized` — the bank approved the payment.
        - `Declined` — the bank refused the payment. The request itself was valid, so it is
          returned with a 200 and the payment is stored.
        - `Rejected` — the gateway never sent the payment to the bank, because the request was
          invalid or could not be processed. It is returned with a 4xx or 5xx and nothing is
          stored.""",
    example = "Authorized",
    allowableValues = {"Authorized", "Declined", "Rejected"})
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
