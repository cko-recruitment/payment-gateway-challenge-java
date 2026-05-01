package com.checkout.payment.gateway.shared.configuration.properties;

public record ClientProperties(String url, Integer connectTimeoutInMs, Integer readTimeoutInMs) { }
