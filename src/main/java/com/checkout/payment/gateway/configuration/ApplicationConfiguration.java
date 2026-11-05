package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.properties.BankProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfiguration.class);

  @Bean
  public RestClient bankRestClient(RestClient.Builder builder, BankProperties bankProperties) {
    LOG.info("Calling the acquiring bank at {} (connect timeout {}, read timeout {})",
        bankProperties.apiUrl(), bankProperties.connectTimeout(), bankProperties.readTimeout());
    return builder
        .baseUrl(bankProperties.apiUrl())
        .requestFactory(ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(bankProperties.connectTimeout())
                .withReadTimeout(bankProperties.readTimeout())))
        .build();
  }
}
