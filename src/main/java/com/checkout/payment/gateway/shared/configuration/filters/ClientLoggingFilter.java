package com.checkout.payment.gateway.shared.configuration.filters;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClientLoggingFilter implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    log.info("Calling [{}-{}]", request.getMethod(), request.getURI());
    ClientHttpResponse response = execution.execute(request, body);
    log.info("Response status: [{}]", response.getStatusCode());
    return response;
  }
}