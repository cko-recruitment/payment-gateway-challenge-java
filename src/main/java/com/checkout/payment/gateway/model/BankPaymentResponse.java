package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BankPaymentResponse(
    @NotNull
    @JsonProperty("authorized")
    boolean authorized,
    @NotNull
    @JsonProperty("authorization_code")
    String authorizationCode
) implements Serializable {

}
