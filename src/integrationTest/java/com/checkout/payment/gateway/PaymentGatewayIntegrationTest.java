package com.checkout.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.container.BankServerContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = PaymentGatewayApplication.class)
class PaymentGatewayIntegrationTest extends BankServerContainer {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<PostPaymentResponse> executePostPayment(String cardNumber) {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PostPaymentRequest request = new PostPaymentRequest(
                cardNumber,
                12,
                2026,
                PaymentCurrency.USD,
                100,
                "123"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", paymentId.toString());
        headers.set("X-Merchant-Id", merchantId.toString());

        HttpEntity<PostPaymentRequest> entity = new HttpEntity<>(request, headers);
        String url = "https://localhost:" + port + "/api/payment";

        return restTemplate.exchange(url, HttpMethod.POST, entity, PostPaymentResponse.class);
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        ResponseEntity<PostPaymentResponse> response = executePostPayment("1234567890123451");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().name()).isEqualTo("AUTHORIZED");
    }

    @Test
    void shouldDeclinePaymentWhenBankSimulatorReturnsFalse() {
        ResponseEntity<PostPaymentResponse> response = executePostPayment("1234567890123452");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().name()).isEqualTo("DECLINED");
    }
}
