package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.BankInvalidResponseException;
import com.checkout.payment.gateway.exception.BankRejectedException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.internal.BankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class BankClientTest {
  public static final String BANK_URL = "http://testbank";
  public static final String BANK_ENDPOINT = BANK_URL + "/payments";

  private MockRestServiceServer server;
  private BankClient bankClient;

  @BeforeEach
  void setup() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    bankClient = new BankClient(restTemplate, BANK_URL);
  }

  @Test
  void whenPaymentIsAuthorizedThenReturnBankResponse() {
    var responseBody = """
        {
            "authorized": true,
            "authorization_code": "abc-123"
        }
        """;

    server.expect(requestTo(BANK_ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    BankResponse response = bankClient.processPayment(validRequest());

    assertThat(response.isAuthorized()).isTrue();

    server.verify();
  }

  @Test
  void whenEmptyResponseThenThrowInvalidResponse() {
    var responseBody = "";

    server.expect(requestTo(BANK_ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> bankClient.processPayment(validRequest()))
        .isInstanceOf(BankInvalidResponseException.class);
  }

  @Test
  void whenBankReturns503ThenRetryAndThrowUnavailable() {
    server.expect(times(3), requestTo(BANK_ENDPOINT))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> bankClient.processPayment(validRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void whenBankReturns400ThenThrowRejected() {
    server.expect(requestTo(BANK_ENDPOINT))
        .andRespond(withBadRequest());

    assertThatThrownBy(() -> bankClient.processPayment(validRequest()))
        .isInstanceOf(BankRejectedException.class);
  }

  @Test
  void whenConnectionFailsThenThrowUnavailable() {

    RestTemplate disconnectedTemplate = new RestTemplate();
    BankClient client = new BankClient(disconnectedTemplate, BANK_URL);

    assertThatThrownBy(() -> client.processPayment(validRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }

  private PostPaymentRequest validRequest() {
    var request = new PostPaymentRequest();
    request.setCardNumber("123456789");

    return request;
  }
}