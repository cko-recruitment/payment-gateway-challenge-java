package com.checkout.payment.gateway.acquirer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AcquirerPaymentResponse(
    Boolean authorized,
    @JsonProperty("authorization_code") String authorizationCode
) {

}
