package com.checkout.payment.gateway.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Pins the JSON the acquiring bank sees. The field names come from
 * spring.jackson.property-naming-strategy, not from per-field annotations, so this guards against
 * the bank client being rebuilt in a way that bypasses the application's Jackson configuration.
 */
@SpringBootTest
class BankPaymentsProcessorWireFormatTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired
  private RestClient bankRestClient;

  @Test
  void sendsSnakeCaseFieldNamesAndReadsSnakeCaseResponse() throws Exception {
    StringBuilder sentBody = new StringBuilder();
    ClientHttpRequestFactory recordingFactory =
        (uri, method) -> new MockClientHttpRequest(method, uri) {
          @Override
          protected ClientHttpResponse executeInternal() {
            sentBody.append(getBodyAsString());
            MockClientHttpResponse response = new MockClientHttpResponse(
                "{\"authorized\":true,\"authorization_code\":\"abc-123\"}".getBytes(), 200);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
          }
        };

    BankPaymentsProcessor processor = new BankPaymentsProcessor(
        bankRestClient.mutate().requestFactory(recordingFactory).build());

    BankPaymentResponse response = processor.process(
        new BankPaymentRequest("4111111111111111", "04/2030", "GBP", 100, "123"));

    JsonNode sent = JSON.readTree(sentBody.toString());
    assertEquals(List.of("amount", "card_number", "currency", "cvv", "expiry_date"),
        sent.properties().stream().map(Map.Entry::getKey).sorted().toList());
    assertEquals("4111111111111111", sent.get("card_number").asText());
    assertEquals("04/2030", sent.get("expiry_date").asText());
    assertEquals("GBP", sent.get("currency").asText());
    assertEquals(100, sent.get("amount").asInt());
    assertEquals("123", sent.get("cvv").asText());

    assertTrue(response.isAuthorized());
    assertEquals("abc-123", response.authorizationCode());
  }
}
