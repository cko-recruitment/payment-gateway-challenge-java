package com.checkout.payment.gateway.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Builder;

@Builder
public record MounteBankResponse(
    Boolean authorized,
    @JsonProperty("authorization_code") UUID authorizationCode
) {

}
