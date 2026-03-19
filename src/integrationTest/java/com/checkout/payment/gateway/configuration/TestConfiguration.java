package com.checkout.payment.gateway.configuration;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import javax.net.ssl.SSLContext;

@Configuration
public class TestConfiguration {

  @Bean
  @Primary
  public RestTemplateBuilder restTemplateBuilder() throws Exception {
    SSLContext sslContext = new SSLContextBuilder()
        .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
        .build();

    CloseableHttpClient httpClient = HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            //To bypass the SSL handshake
            .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build())
            .build())
        .build();

    return new RestTemplateBuilder()
        .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient));
  }

}
