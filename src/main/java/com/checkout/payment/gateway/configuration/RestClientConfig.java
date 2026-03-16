package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.configuration.properties.AcquiringBankProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AcquiringBankProperties.class)
public class RestClientConfig {
  @Bean
  public RestClient.Builder restClientBuilder(AcquiringBankProperties properties) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(properties.getConnectTimeout())
        .build(); //establish TCP connection

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.getReadTimeout()); //receive response

    return RestClient.builder()
        .requestFactory(requestFactory);
  }

}
