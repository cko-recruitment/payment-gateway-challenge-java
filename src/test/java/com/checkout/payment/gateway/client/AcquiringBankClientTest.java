package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.Client.AcquiringBankClient;
import com.checkout.payment.gateway.exception.BankIntegrationException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankingClientRequest;
import com.checkout.payment.gateway.model.BankingClientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcquiringBankClientTest {

  private RestClient.Builder builder;
  private RestClient restClient;

  private AcquiringBankClient client;

  private BankingClientRequest request;

  @BeforeEach
  void setup() {

    builder = mock(RestClient.Builder.class);
    restClient = mock(RestClient.class);

    when(builder.baseUrl(anyString())).thenReturn(builder);
    when(builder.build()).thenReturn(restClient);

    client = Mockito.spy(new AcquiringBankClient(builder, "http://bank"));

    request = new BankingClientRequest();
  }

  @Test
  void shouldReturnAuthorizedResponse() {

    BankingClientResponse response = new BankingClientResponse();
    response.setAuthorized(true);

    doReturn(response)
        .when(client)
        .executeAuthorize(any());

    BankingClientResponse result = client.authorize(request);

    assertTrue(result.getAuthorized());
  }

  @Test
  void shouldRetryWhenBankUnavailableThenSucceed() {

    BankingClientResponse successResponse = new BankingClientResponse();
    successResponse.setAuthorized(true);

    doThrow(new BankUnavailableException("bank down"))
        .doReturn(successResponse)
        .when(client)
        .executeAuthorize(any());

    BankingClientResponse result = client.authorize(request);

    assertTrue(result.getAuthorized());

    verify(client, times(2))
        .executeAuthorize(any());
  }

  @Test
  void shouldRetryThreeTimesThenThrowBankUnavailable() {

    doThrow(new BankUnavailableException("bank unavailable"))
        .when(client)
        .executeAuthorize(any());

    BankUnavailableException exception =
        assertThrows(
            BankUnavailableException.class,
            () -> client.authorize(request)
        );

    assertTrue(exception.getMessage().contains("Failed to communicate"));

    verify(client, times(3))
        .executeAuthorize(any());
  }

  @Test
  void shouldNotRetryForBankIntegrationException() {

    doThrow(new BankIntegrationException("invalid request"))
        .when(client)
        .executeAuthorize(any());

    assertThrows(
        BankIntegrationException.class,
        () -> client.authorize(request)
    );

    verify(client, times(1))
        .executeAuthorize(any());
  }

  @Test
  void shouldRetryForResourceAccessException() {

    BankingClientResponse success = new BankingClientResponse();
    success.setAuthorized(true);

    doThrow(new ResourceAccessException("timeout"))
        .doReturn(success)
        .when(client)
        .executeAuthorize(any());

    BankingClientResponse response = client.authorize(request);

    assertTrue(response.getAuthorized());

    verify(client, times(2))
        .executeAuthorize(any());
  }

}
