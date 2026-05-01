package com.checkout.payment.gateway.client.dto;

import com.checkout.payment.gateway.shared.enums.Currency;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MounteBankRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String cvv,
    Currency currency,
    Integer amount
) {

}
