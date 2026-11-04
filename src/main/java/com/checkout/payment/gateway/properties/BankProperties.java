package com.checkout.payment.gateway.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payments.bank")
public record BankProperties(@NotBlank String apiUrl) {

}
