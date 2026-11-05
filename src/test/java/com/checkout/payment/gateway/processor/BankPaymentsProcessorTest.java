package com.checkout.payment.gateway.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.BankFailure;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.properties.BankProperties;
import java.net.ConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/**
 * Drives the real bankRestClient bean with only its transport swapped out, so the JSON asserted
 * here is the JSON the bank would receive in production.
 */
@SpringBootTest
class BankPaymentsProcessorTest {

  private static final String AUTHORISED =
      "{\"authorized\":true,\"authorization_code\":\"0bb07405-6d44-4b50-a14f-7ae0beff13ad\"}";

  @Autowired
  private RestClient bankRestClient;

  @Autowired
  private BankProperties bankProperties;

  private BankPaymentsProcessor processor;
  private MockRestServiceServer bank;
  private String paymentsUrl;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = bankRestClient.mutate();
    this.bank = MockRestServiceServer.bindTo(builder).build();
    this.processor = new BankPaymentsProcessor(builder.build());
    this.paymentsUrl = bankProperties.apiUrl() + "/payments";
  }

  private static BankPaymentRequest request() {
    return new BankPaymentRequest("4111111111111111", "12/2030", "GBP", 1000, "123");
  }

  private BankPaymentResponse processWith(ResponseCreator bankResponse) {
    bank.expect(requestTo(paymentsUrl)).andExpect(method(HttpMethod.POST)).andRespond(bankResponse);
    BankPaymentResponse response = processor.process(request());
    bank.verify();
    return response;
  }

  private void assertFailsWith(ResponseCreator bankResponse, BankFailure expected) {
    bank.expect(requestTo(paymentsUrl)).andExpect(method(HttpMethod.POST)).andRespond(bankResponse);
    BankException ex = assertThrows(BankException.class, () -> processor.process(request()));
    assertEquals(expected, ex.failure());
    bank.verify();
  }

  @Test
  @DisplayName("the bank's field names come from the Jackson configuration, not from annotations")
  void outboundRequestUsesTheBanksFieldNames() {
    bank.expect(requestTo(paymentsUrl))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {
              "card_number": "4111111111111111",
              "expiry_date": "12/2030",
              "currency": "GBP",
              "amount": 1000,
              "cvv": "123"
            }""", true))
        .andRespond(withSuccess(AUTHORISED, MediaType.APPLICATION_JSON));

    processor.process(request());

    bank.verify();
  }

  @Test
  void authorisedPaymentIsReturned() {
    BankPaymentResponse response = processWith(withSuccess(AUTHORISED,
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
