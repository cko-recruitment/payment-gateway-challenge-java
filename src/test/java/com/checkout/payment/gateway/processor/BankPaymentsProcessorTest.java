package com.checkout.payment.gateway.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.BankFailure;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.ConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class BankPaymentsProcessorTest {

  private static final String BANK_URL = "http://bank.test/payments";

  private BankPaymentsProcessor processor;
  private MockRestServiceServer bank;

  @BeforeEach
  void setUp() {
    // Mirrors spring.jackson.property-naming-strategy, which the production bank client gets from
    // Boot's auto-configured RestClient.Builder but a hand-built one does not.
    ObjectMapper mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();
    RestClient.Builder builder = RestClient.builder()
        .baseUrl("http://bank.test")
        .messageConverters(converters -> converters.replaceAll(
            converter -> converter instanceof MappingJackson2HttpMessageConverter
                ? new MappingJackson2HttpMessageConverter(mapper)
                : converter));
    this.bank = MockRestServiceServer.bindTo(builder).build();
    this.processor = new BankPaymentsProcessor(builder.build());
  }

  private static BankPaymentRequest request() {
    return new BankPaymentRequest("4111111111111111", "12/2030", "GBP", 1000, "123");
  }

  private BankPaymentResponse processWith(ResponseCreator bankResponse) {
    bank.expect(requestTo(BANK_URL)).andExpect(method(HttpMethod.POST)).andRespond(bankResponse);
    BankPaymentResponse response = processor.process(request());
    bank.verify();
    return response;
  }

  private void assertFailsWith(ResponseCreator bankResponse, BankFailure expected) {
    bank.expect(requestTo(BANK_URL)).andExpect(method(HttpMethod.POST)).andRespond(bankResponse);
    BankException ex = assertThrows(BankException.class, () -> processor.process(request()));
    assertEquals(expected, ex.failure());
    bank.verify();
  }

  @Test
  void authorisedPaymentIsReturned() {
    BankPaymentResponse response = processWith(withSuccess(
        "{\"authorized\":true,\"authorization_code\":\"0bb07405-6d44-4b50-a14f-7ae0beff13ad\"}",
        MediaType.APPLICATION_JSON));

    assertTrue(response.isAuthorized());
    assertEquals("0bb07405-6d44-4b50-a14f-7ae0beff13ad", response.authorizationCode());
  }

  @Test
  @DisplayName("a decline is a valid answer, not a failure")
  void declinedPaymentIsReturned() {
    BankPaymentResponse response = processWith(withSuccess(
        "{\"authorized\":false,\"authorization_code\":\"\"}", MediaType.APPLICATION_JSON));

    assertFalse(response.isAuthorized());
  }

  @Test
  void bankClientErrorIsAGatewayFault() {
    assertFailsWith(withStatus(HttpStatus.BAD_REQUEST)
            .body("{\"error_message\":\"Not all required properties were sent in the request\"}")
            .contentType(MediaType.APPLICATION_JSON),
        BankFailure.BANK_REJECTED_REQUEST);
  }

  @Test
  void bankServiceUnavailableResultsInBankUnavailableFailure() {
    assertFailsWith(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .body("{}").contentType(MediaType.APPLICATION_JSON), BankFailure.BANK_UNAVAILABLE);
  }

  @Test
  void bankInternalServerErrorResultsInBankUnavailableFailure() {
    assertFailsWith(withStatus(HttpStatus.INTERNAL_SERVER_ERROR), BankFailure.BANK_UNAVAILABLE);
  }

  @Test
  void bankCannotBeReachedResultsInBankUnreachableFailure() {
    assertFailsWith(withException(new ConnectException("Connection refused")),
        BankFailure.BANK_UNREACHABLE);
  }

  @Test
  void malformedResponseBodyResultsInBankUnreachableFailure() {
    assertFailsWith(withSuccess("{\"authorized\":tru", MediaType.APPLICATION_JSON),
        BankFailure.BANK_UNREACHABLE);
  }

  @Test
  void emptyResponseBodyResultsInBankUnreachableFailure() {
    assertFailsWith(withSuccess(), BankFailure.BANK_UNREACHABLE);
  }

  @Test
  void responseWithoutAnAuthorisationDecisionResultsInBankUnreachableFailure() {
    assertFailsWith(withSuccess("{\"authorization_code\":\"abc\"}", MediaType.APPLICATION_JSON),
        BankFailure.BANK_UNREACHABLE);
  }

  @Test
  void outboundRequestDoesNotPrintThePanOrCvv() {
    String printed = request().toString();

    assertTrue(printed.contains("****1111"));
    assertFalse(printed.contains("4111111111111111"));
    assertFalse(printed.contains("123"));
  }
}
