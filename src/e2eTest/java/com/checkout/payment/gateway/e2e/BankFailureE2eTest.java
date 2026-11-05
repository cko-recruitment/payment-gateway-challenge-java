package com.checkout.payment.gateway.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class BankFailureE2eTest extends AbstractE2eTest {

  /** The committed imposter answers 503 for any card number ending in zero. */
  private static final String CARD_BANK_ERROR = "2222405343248870";

  private static final int REJECTING_BANK_PORT = 8081;

  @Test
  @DisplayName("a 5xx from the bank is reported as 503, not as a bare 500")
  void bankUnavailableReturns503() throws Exception {
    HttpResponse<String> response =
        postPayment(paymentRequest(CARD_BANK_ERROR, "GBP", 100, "123"));

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(contentType(response)).startsWith("application/problem+json");

    String body = response.body();
    String title = read(body, "$.title");
    String detail = read(body, "$.detail");
    Integer status = read(body, "$.status");
    String paymentStatus = read(body, "$.payment_status");
    Object errors = read(body, "$.error");

    assertThat(title).isEqualTo("Request failed");
    assertThat(detail).isEqualTo("The acquiring bank is currently unavailable.");
    assertThat(status).isEqualTo(503);
    assertThat(paymentStatus).isEqualTo("Rejected");
    // ProblemDetail serialises with NON_EMPTY, so the empty violation list is left out
    // entirely rather than appearing as []. Nothing was wrong with the request itself.
    assertThat(errors).as("field violations are omitted for a bank failure").isNull();
  }

  @Test
  @DisplayName("a bank that cannot be reached is reported as 502")
  void bankUnreachableReturns502() throws Exception {
    // A fake port where nothing should be present
    try (GenericContainer<?> gateway = E2eEnvironment.gatewayPointingAt("http://127.0.0.1:1")) {
      gateway.start();

      HttpResponse<String> response = postPaymentTo(E2eEnvironment.baseUrlOf(gateway),
          authorizedRequest());

      assertThat(response.statusCode()).isEqualTo(502);
      assertThat(contentType(response)).startsWith("application/problem+json");

      String body = response.body();
      String detail = read(body, "$.detail");
      String paymentStatus = read(body, "$.payment_status");

      assertThat(detail).isEqualTo("The acquiring bank could not be reached.");
      assertThat(paymentStatus).isEqualTo("Rejected");
    }
  }
}
