package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankPaymentResponse(
        @JsonProperty("authorized") boolean authorized,
        @JsonProperty("authorization_code") String authorizationCode
) {}
