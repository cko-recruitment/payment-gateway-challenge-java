package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record BankPaymentResponse(
    @NotNull
    @JsonProperty("authorized")
    boolean authorized,
    @NotNull
    @JsonProperty("authorization_code")
    String authorizationCode
) implements Serializable {

}
