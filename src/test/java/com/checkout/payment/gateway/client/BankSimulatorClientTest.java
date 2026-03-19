package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankSimulatorClientTest {

    private WireMockServer wireMockServer;
    private BankSimulatorClient bankSimulatorClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        
        String baseUrl = "http://localhost:" + wireMockServer.port() + "/payments";
        bankSimulatorClient = new BankSimulatorClient(WebClient.builder(), baseUrl);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldReturnCorrectResponseWhenBankReturnsSuccess() {
        stubFor(post(urlEqualTo("/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"authorized\": true, \"authorization_code\": \"auth-123\"}")));

        BankPaymentRequest request = new BankPaymentRequest("1234567890123456", "12/2026", PaymentCurrency.USD, 1000, "123");
        BankPaymentResponse response = bankSimulatorClient.processPayment(request);

        assertThat(response.authorized()).isTrue();
        assertThat(response.authorizationCode()).isEqualTo("auth-123");
    }

    @Test
    void shouldRetryAndFailWhenBankReturns5xx() {
        stubFor(post(urlEqualTo("/payments"))
                .willReturn(aResponse().withStatus(500)));

        BankPaymentRequest request = new BankPaymentRequest("1234567890123456", "12/2026", PaymentCurrency.USD, 1000, "123");
        
        assertThrows(RuntimeException.class, () -> {
            bankSimulatorClient.processPayment(request);
        });

        // 1 initial attempt + 3 retries = 4 total requests
        verify(4, postRequestedFor(urlEqualTo("/payments")));
    }

    @Test
    void shouldNotRetryWhenBankReturns4xx() {
        stubFor(post(urlEqualTo("/payments"))
                .willReturn(aResponse().withStatus(400)));

        BankPaymentRequest request = new BankPaymentRequest("1234567890123456", "12/2026", PaymentCurrency.USD, 1000, "123");

        assertThrows(WebClientResponseException.BadRequest.class, () -> {
            bankSimulatorClient.processPayment(request);
        });

        verify(1, postRequestedFor(urlEqualTo("/payments")));
    }
}
