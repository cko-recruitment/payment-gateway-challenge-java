package com.checkout.payment.gateway.Client;

import com.checkout.payment.gateway.exception.BankIntegrationException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankingClientRequest;
import com.checkout.payment.gateway.model.BankingClientResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AcquiringBankClient {

  private final RestClient restClient;

  public AcquiringBankClient(RestClient.Builder builder,
      @Value("${bank.base-url}") String bankBaseUrl) {
    this.restClient = builder
        .baseUrl(bankBaseUrl)
        .build();
  }

  public BankingClientResponse authorize(BankingClientRequest request) {
    try {
      BankingClientResponse response = restClient.post()
          .uri("/payments")
          .header(HttpHeaders.CONTENT_TYPE, "application/json")
          .body(request)
          .retrieve()
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            throw new BankUnavailableException("Acquiring bank is unavailable");
          })
          .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            throw new BankIntegrationException("Acquiring bank rejected the request");
          })
          .body(BankingClientResponse.class);

      if (response == null) {
        throw new BankUnavailableException("Acquiring bank returned an empty response");
      }

      return response;
    } catch (ResourceAccessException ex) {
      throw new BankUnavailableException("Timed out or failed to connect to acquiring bank", ex);
    } catch (RestClientException ex) {
      throw new BankUnavailableException("Failed to communicate with acquiring bank", ex);
    }
  }
}
