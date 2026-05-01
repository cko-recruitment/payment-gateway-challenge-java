package com.checkout.payment.gateway.shared.configuration;

import com.checkout.payment.gateway.shared.configuration.properties.ClientConfigurationProperties;
import com.checkout.payment.gateway.shared.configuration.properties.ClientProperties;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class RestConfiguration {

  private final List<ClientHttpRequestInterceptor> interceptors;
  private final ClientConfigurationProperties clientConfigurationProperties;

  @Bean
  public RestClient mountBankClient() {
    ClientProperties properties = clientConfigurationProperties.mounteBank();
    return RestClient.builder()
        .baseUrl(properties.url())
        .defaultHeader("Content-Type", "application/json")
        .requestFactory(factory(properties))
        .requestInterceptors(list -> list.addAll(interceptors))
        .build();
  }

  private SimpleClientHttpRequestFactory factory(ClientProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutInMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutInMs()));
    return factory;
  }

}
