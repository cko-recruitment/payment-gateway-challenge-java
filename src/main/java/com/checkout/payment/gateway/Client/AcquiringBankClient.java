package com.checkout.payment.gateway.Client;

import com.checkout.payment.gateway.exception.BankIntegrationException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankingClientRequest;
import com.checkout.payment.gateway.model.BankingClientResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AcquiringBankClient {
  private static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_BACKOFF_MILLIS = 200L;

  private final RestClient restClient;

  public AcquiringBankClient(RestClient.Builder builder,
      @Value("${bank.base-url}") String bankBaseUrl) {
    this.restClient = builder
        .baseUrl(bankBaseUrl)
        .build();
  }

  public BankingClientResponse authorize(BankingClientRequest request) {
    int attempt = 1;

    while (attempt <= MAX_ATTEMPTS) {
      try {
        return executeAuthorize(request);
      } catch (Exception ex) {
        if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
          throw mapToGatewayException(ex, attempt);
        }

        sleepBeforeRetry(attempt);
        attempt++;
      }
    }

    throw new BankUnavailableException("Unexpected retry flow while calling acquiring bank");
  }

  public BankingClientResponse executeAuthorize(BankingClientRequest request) {
    BankingClientResponse response = restClient.post()
        .uri("/payments")
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
          throw new BankIntegrationException("Acquiring bank rejected the request");
        })
        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
          throw new BankUnavailableException("Acquiring bank is unavailable");
        })
        .body(BankingClientResponse.class);

    if (response == null) {
      throw new BankUnavailableException("Acquiring bank returned an empty response");
    }

    return response;
  }

  private boolean isRetryable(Exception ex) {
    return ex instanceof BankUnavailableException
        || ex instanceof HttpServerErrorException
        || ex instanceof ResourceAccessException;
  }

  private RuntimeException mapToGatewayException(Exception ex, int attempt) {
    if (ex instanceof BankIntegrationException bankIntegrationException) {
      return bankIntegrationException;
    }

    if (ex instanceof HttpClientErrorException) {
      return new BankIntegrationException("Acquiring bank rejected the request", ex);
    }

    if (ex instanceof BankUnavailableException bankUnavailableException) {
      return new BankUnavailableException(
          "Failed to communicate with acquiring bank after " + attempt + " attempts",
          bankUnavailableException
      );
    }

    if (ex instanceof HttpServerErrorException
        || ex instanceof ResourceAccessException
        || ex instanceof RestClientException) {
      return new BankUnavailableException(
          "Failed to communicate with acquiring bank after " + attempt + " attempts",
          ex
      );
    }

    return new BankUnavailableException("Unexpected error while calling acquiring bank", ex);
  }

  private void sleepBeforeRetry(int attempt) {
    long backoff = INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1));

    try {
      Thread.sleep(backoff);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BankUnavailableException("Retry interrupted while calling acquiring bank", ex);
    }
  }
}