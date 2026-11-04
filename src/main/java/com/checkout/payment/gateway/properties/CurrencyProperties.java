package com.checkout.payment.gateway.properties;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payments.currencies")
public record CurrencyProperties(@NotEmpty Set<String> supportedCurrencies) {

}
