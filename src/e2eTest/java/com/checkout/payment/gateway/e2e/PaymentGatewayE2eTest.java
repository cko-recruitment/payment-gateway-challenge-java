package com.checkout.payment.gateway.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * End-to-end tests against the real application image talking to the real bank simulator.
 *
 * <p>The simulator decides the outcome from the <em>last digit</em> of the card number: odd
 * authorizes, even and non-zero declines.
 */
class PaymentGatewayE2eTest extends AbstractE2eTest {

  private static final String CARD_AUTHORIZED = "2222405343248877";
  private static final String CARD_DECLINED = "2222405343248112";

  @Test
  @DisplayName("an authorized payment returns 200 with the full payment representation")
  void authorizedPaymentReturnsOkAndAuthorized() throws Exception {
    HttpResponse<String> response = postPayment(authorizedRequest());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(contentType(response)).startsWith("application/json");

    String body = response.body();
    assertThatCode(() -> UUID.fromString(read(body, "$.id"))).doesNotThrowAnyException();

    String paymentStatus = read(body, "$.payment_status");
    Integer status = read(body, "$.status");
    String currency = read(body, "$.currency");
    Integer amount = read(body, "$.amount");
    Integer expiryMonth = read(body, "$.expiry_month");
    Integer expiryYear = read(body, "$.expiry_year");
    Object lastFour = read(body, "$.card_number_last_four");
    Object rejectionMessage = read(body, "$.rejection_message");

    assertThat(paymentStatus).isEqualTo("Authorized");
    assertThat(status).isEqualTo(200);
    assertThat(currency).isEqualTo("GBP");
    assertThat(amount).isEqualTo(100);
    assertThat(expiryMonth).isEqualTo(FUTURE_EXPIRY.getMonthValue());
    assertThat(expiryYear).isEqualTo(FUTURE_EXPIRY.getYear());

    // Must stay a JSON string: as a number, a PAN ending 0877 would lose its leading zero.
    assertThat(lastFour).isInstanceOf(String.class).isEqualTo("8877");

    assertThat(rejectionMessage).as("rejection_message is omitted when null").isNull();
  }

  @Test
  @DisplayName("a declined payment is still HTTP 200, with the decline carried in the body")
  void declinedPaymentStillReturnsOk() throws Exception {
    HttpResponse<String> response = postPayment(paymentRequest(CARD_DECLINED, "GBP", 250, "456"));

    String paymentStatus = read(response.body(), "$.payment_status");
    String lastFour = read(response.body(), "$.card_number_last_four");

    // A decline is a business outcome, not a transport error.
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(paymentStatus).isEqualTo("Declined");
    assertThat(lastFour).isEqualTo("8112");
  }

  @Test
  @DisplayName("a payment can be fetched by id and matches what was returned when created")
  void paymentCanBeRetrievedAfterCreation() throws Exception {
    HttpResponse<String> created = postPayment(authorizedRequest());
    assertThat(created.statusCode()).isEqualTo(200);

    String id = read(created.body(), "$.id");
    HttpResponse<String> fetched = getPayment(id);

    assertThat(fetched.statusCode()).isEqualTo(200);
    JSONAssert.assertEquals(created.body(), fetched.body(), JSONCompareMode.STRICT);
  }

  @Test
  @DisplayName("an invalid request is rejected at the edge and never reaches the bank")
  void invalidRequestIsRejectedWithoutReachingBank() throws Exception {
    int before = bankRequestCount();

    // Unsupported currency and a too-short CVV: two violations, so the array is exercised.
    HttpResponse<String> response = postPayment(paymentRequest(CARD_AUTHORIZED, "AUD", 100, "12"));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(contentType(response)).startsWith("application/problem+json");

    String body = response.body();
    String title = read(body, "$.title");
    String detail = read(body, "$.detail");
    Integer status = read(body, "$.status");
    String paymentStatus = read(body, "$.payment_status");
    // The property is `error`, singular, and field names are snake_case.
    List<String> fields = read(body, "$.error[*].field");
    List<String> maskedCvv = read(body, "$.error[?(@.field == 'cvv')].rejected_value");

    assertThat(title).isEqualTo("Validation failed");
    assertThat(detail).isEqualTo("One or more fields are invalid");
    assertThat(status).isEqualTo(400);
    assertThat(paymentStatus).isEqualTo("Rejected");
    assertThat(fields).containsExactlyInAnyOrder("currency", "cvv");

    // Masking the rejected value is a security control, so assert it rather than assume it.
    assertThat(maskedCvv).containsExactly("***");

    assertThat(bankRequestCount())
        .as("validation must reject before any call to the bank")
        .isEqualTo(before);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "card number too short,   '123',              GBP, 100, '123'",
      "card number non-numeric, '22224053432488AB', GBP, 100, '123'",
      "amount not positive,     '2222405343248877', GBP, 0,   '123'",
      "cvv too long,            '2222405343248877', GBP, 100, '12345'",
      "unsupported currency,    '2222405343248877', AUD, 100, '123'",
  })
  void malformedFieldsAreRejected(String description, String card, String currency, int amount,
      String cvv) throws Exception {
    HttpResponse<String> response = postPayment(paymentRequest(card, currency, amount, cvv));
    String paymentStatus = read(response.body(), "$.payment_status");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(paymentStatus).isEqualTo("Rejected");
  }

  @Test
  @DisplayName("fetching an unknown payment id returns 404")
  void unknownPaymentIdReturns404() throws Exception {
    HttpResponse<String> response = getPayment(UUID.randomUUID().toString());

    String detail = read(response.body(), "$.detail");
    Integer status = read(response.body(), "$.status");
    String paymentStatus = read(response.body(), "$.payment_status");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(contentType(response)).startsWith("application/problem+json");
    assertThat(detail).isEqualTo("Page not found");
    assertThat(status).isEqualTo(404);
    assertThat(paymentStatus).isEqualTo("Rejected");
  }

  @Test
  @DisplayName("a path variable that is not a UUID returns 400")
  void nonUuidPaymentIdReturns400() throws Exception {
    HttpResponse<String> response = getPayment("not-a-uuid");
    String paymentStatus = read(response.body(), "$.payment_status");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(paymentStatus).isEqualTo("Rejected");
  }

  private static String contentType(HttpResponse<String> response) {
    return response.headers().firstValue("Content-Type").orElse("");
  }
}
