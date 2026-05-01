package com.checkout.payment.gateway.shared.configuration.properties;

import com.checkout.payment.gateway.shared.enums.Currency;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "configuration")
public record ApplicationProperties(List<Currency> currencies) {

}
