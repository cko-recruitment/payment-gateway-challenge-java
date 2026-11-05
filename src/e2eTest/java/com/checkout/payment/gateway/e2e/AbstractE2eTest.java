package com.checkout.payment.gateway.e2e;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * HTTP helpers for the end-to-end suite.
 *
 * <p>Uses the JDK's {@link HttpClient} rather than Spring's clients: the e2e source set is
 * black-box and deliberately excludes the application's own classes, so spring-web is not on
 * this classpath. It also never throws on a non-2xx response, which matters when the
 * assertions are about 400 and 404 responses.
 */
abstract class AbstractE2eTest {

  /** Suppresses path-not-found exceptions so an absent field reads as null. */
  private static final Configuration LENIENT =
      Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

  private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .version(HttpClient.Version.HTTP_1_1)
      .build();

  /** Computed, not hardcoded, so the @ExpiryInFuture constraint cannot date the suite. */
  protected static final YearMonth FUTURE_EXPIRY = YearMonth.now(ZoneOffset.UTC).plusYears(2);

  protected static HttpResponse<String> postPayment(String jsonBody) throws Exception {
    return send(HttpRequest.newBuilder(URI.create(E2eEnvironment.gatewayBaseUrl() + "/payment"))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(jsonBody, UTF_8)));
  }

  protected static HttpResponse<String> getPayment(String id) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create(E2eEnvironment.gatewayBaseUrl() + "/payment/" + id)));
  }

  private static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return CLIENT.send(builder.timeout(Duration.ofSeconds(20)).build(), BodyHandlers.ofString());
  }

  /** How many requests the bank simulator has served, read from its admin API. */
  protected static int bankRequestCount() throws Exception {
    HttpResponse<String> imposter = send(HttpRequest.newBuilder(URI.create(
        E2eEnvironment.bankAdminBaseUrl() + "/imposters/" + E2eEnvironment.bankImposterPort())));
    return JsonPath.read(imposter.body(), "$.numberOfRequests");
  }

  /** Reads a path, returning null rather than throwing when it is absent. */
  protected static <T> T read(String body, String path) {
    return JsonPath.using(LENIENT).parse(body).read(path);
  }

  protected static String paymentRequest(String cardNumber, String currency, int amount,
      String cvv) {
    return """
        {
          "card_number": "%s",
          "expiry_month": %d,
          "expiry_year": %d,
          "currency": "%s",
          "amount": %d,
          "cvv": "%s"
        }""".formatted(cardNumber, FUTURE_EXPIRY.getMonthValue(), FUTURE_EXPIRY.getYear(),
        currency, amount, cvv);
  }

  /** A request that the gateway will authorize: card number ending in an odd digit. */
  protected static String authorizedRequest() {
    return paymentRequest("2222405343248877", "GBP", 100, "123");
  }
}
