package com.checkout.payment.gateway.acquirer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.AcquirerException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ExtendWith(OutputCaptureExtension.class)
class AcquirerClientTest {

  private final PostPaymentRequest request =
      new PostPaymentRequest("12345678901234", 4, 2099, "GBP", 10, "123");
  private MockRestServiceServer paymentGateway;
  private AcquirerClient acquirerClient;

  @BeforeEach
  void setup() {
    RestTemplate template = new RestTemplate();
    paymentGateway = MockRestServiceServer.bindTo(template).build();
    acquirerClient = new AcquirerClient(template, "http://acquirer/payments");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      true  | {"authorized": true, "authorization_code": "12345678-1234-1234-1234-123456789012"}
      false | {"authorized": false, "authorization_code": ""}
      """)
  void sendsPaymentAndReadsAcquirerOutcome(boolean authorized, String response,
      CapturedOutput output) {
    paymentGateway.expect(requestTo("http://acquirer/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Request-ID", "test-request-id"))
        .andExpect(content().json("""
            {
              "card_number": "12345678901234",
              "expiry_date": "04/2099",
              "currency": "GBP",
              "amount": 10,
              "cvv": "123"
            }
            """))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    MDC.put("requestId", "test-request-id");
    try {
      assertEquals(authorized, acquirerClient.authorize(request));
    } finally {
      MDC.remove("requestId");
    }
    assertTrue(output.getOut().contains("outcome=" + (authorized ? "authorized" : "declined")));
    assertTrue(output.getOut().contains("acquirer_status=200 duration_ms="));
    assertFalse(output.getOut().contains(request.cardNumber()));
    paymentGateway.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "", "{}", "null", "{",
      """
          {"authorized": true}
          """,
      """
          {"authorized": true, "authorization_code": "bad"}
          """
  })
  void invalidAcquirerBodyReturnsBadGateway(String body) {
    paymentGateway.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    AcquirerException error = assertThrows(AcquirerException.class,
        () -> acquirerClient.authorize(request));
    assertFalse(error.isUnavailable());
    paymentGateway.verify();
  }

  @ParameterizedTest
  @CsvSource({"201, false", "400, false", "503, true"})
  void unexpectedHttpStatusReturnsSafeError(int status, boolean unavailable,
      CapturedOutput output) {
    paymentGateway.expect(anything())
        .andRespond(withStatus(HttpStatus.valueOf(status))
            .contentType(MediaType.APPLICATION_JSON)
            .body(status == 201 ? """
                {"authorized": false}
                """ : """
                {"error":"Bank unavailable"}
                """));

    AcquirerException error = assertThrows(AcquirerException.class,
        () -> acquirerClient.authorize(request));
    assertEquals(unavailable, error.isUnavailable());
    assertFalse(error.getMessage().contains("Bank unavailable"));
    String category = status == 201 ? "invalid_response" : "http_error";
    assertTrue(output.getOut().contains("outcome=" + category + " acquirer_status=" + status));
    assertFalse(output.getOut().contains("Bank unavailable"));
    paymentGateway.verify();
  }

  @Test
  void timeoutIsUnavailableWithoutRetry(CapturedOutput output) {
    paymentGateway.expect(anything()).andRespond(withException(new SocketTimeoutException()));

    AcquirerException error = assertThrows(AcquirerException.class,
        () -> acquirerClient.authorize(request));
    assertTrue(error.isUnavailable());
    assertTrue(output.getOut().contains("outcome=timeout acquirer_status=0 duration_ms="));
    paymentGateway.verify();
  }
}
