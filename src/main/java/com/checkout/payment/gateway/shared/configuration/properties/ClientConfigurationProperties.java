package com.checkout.payment.gateway.shared.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "client")
public record ClientConfigurationProperties(ClientProperties mounteBank) {

}
