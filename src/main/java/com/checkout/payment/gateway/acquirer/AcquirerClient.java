package com.checkout.payment.gateway.acquirer;

import static com.checkout.payment.gateway.observability.ObservabilityConstants.AUTHORIZED;
import static com.checkout.payment.gateway.observability.ObservabilityConstants.DECLINED;
import static com.checkout.payment.gateway.observability.ObservabilityConstants.REQUEST_ID_HEADER;
import static com.checkout.payment.gateway.observability.ObservabilityConstants.REQUEST_ID_MDC_KEY;

import com.checkout.payment.gateway.acquirer.dto.AcquirerPaymentRequest;
import com.checkout.payment.gateway.acquirer.dto.AcquirerPaymentResponse;
import com.checkout.payment.gateway.exception.AcquirerException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AcquirerClient {

  private static final Logger LOG = LoggerFactory.getLogger(AcquirerClient.class);

  private final RestTemplate restTemplate;
  private final String url;

  public AcquirerClient(RestTemplate restTemplate, @Value("${acquirer.url}") String url) {
    this.restTemplate = restTemplate;
    this.url = url;
  }

  public boolean authorize(PostPaymentRequest request) {
    AcquirerPaymentRequest body = new AcquirerPaymentRequest(request.cardNumber(),
        request.expiryDate(), request.currency(), request.amount(), request.cvv());
    HttpHeaders headers = new HttpHeaders();
    String requestId = MDC.get(REQUEST_ID_MDC_KEY);
    if (requestId != null) {
      headers.set(REQUEST_ID_HEADER, requestId);
    }
    long started = System.nanoTime();
    int acquirerStatus = 0;
    String outcome = "invalid_response";
    try {
      ResponseEntity<AcquirerPaymentResponse> response = restTemplate.postForEntity(
          url, new HttpEntity<>(body, headers), AcquirerPaymentResponse.class);
      acquirerStatus = response.getStatusCode().value();
      AcquirerPaymentResponse result = response.getBody();
      if (response.getStatusCode().value() != 200 || result == null
          || result.authorized() == null) {
        throw new AcquirerException(false);
      }
      if (result.authorized()) {
        try {
          UUID.fromString(result.authorizationCode());
        } catch (IllegalArgumentException | NullPointerException ex) {
          throw new AcquirerException(false);
        }
      }
      outcome = result.authorized() ? AUTHORIZED : DECLINED;
      return result.authorized();
    } catch (ResourceAccessException ex) {
      outcome = ex.getMostSpecificCause() instanceof SocketTimeoutException
          ? "timeout" : "io_failure";
      throw new AcquirerException(true);
    } catch (HttpStatusCodeException ex) {
      acquirerStatus = ex.getStatusCode().value();
      outcome = "http_error";
      throw new AcquirerException(ex.getStatusCode().is5xxServerError());
    } catch (RestClientException ex) {
      throw new AcquirerException(false);
    } finally {
      long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      String message = "acquirer_request outcome={} acquirer_status={} duration_ms={}";
      if (AUTHORIZED.equals(outcome) || DECLINED.equals(outcome)) {
        LOG.info(message, outcome, acquirerStatus, duration);
      } else {
        LOG.warn(message, outcome, acquirerStatus, duration);
      }
    }
  }
}
