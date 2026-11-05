package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.RequestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Validation tests for {@code POST /payment}.
 */
@WebMvcTest(PaymentGatewayController.class)
@TestPropertySource(properties = "payments.currencies.supported-currencies=GBP,USD,EUR")
class PaymentGatewayControllerValidationTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final YearMonth FUTURE = YearMonth.now(ZoneOffset.UTC).plusYears(1);

  private static final String VALID_CARD = "4111111111111111";

  private static final String CARD_EXPIRED =
      "card has expired: the expiry month and year are in the past";

  @Autowired
  private MockMvc mvc;

  @MockBean
  private RequestService requestService;

  // --------------------------------------------------------------------- helpers

  private static Map<String, Object> validBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("card_number", VALID_CARD);
    body.put("expiry_month", FUTURE.getMonthValue());
    body.put("expiry_year", FUTURE.getYear());
    body.put("currency", "GBP");
    body.put("amount", 1000);
    body.put("cvv", "123");
    return body;
  }

  /**
   * A null value here means an explicit JSON null, which is distinct from an absent key.
   */
  private static String bodyWith(String field, Object value) {
    Map<String, Object> body = validBody();
    body.put(field, value);
    return write(body);
  }

  private static String bodyWithout(String field) {
    Map<String, Object> body = validBody();
    body.remove(field);
    return write(body);
  }

  private static String write(Map<String, Object> body) {
    try {
      return JSON.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Builds a body with one field set to a raw JSON fragment, for values a Map cannot express.
   */
  private static String bodyWithRaw(String field, String rawJson) {
    Map<String, Object> body = validBody();
    body.remove(field);
    String rest = write(body);
    return rest.substring(0, rest.length() - 1) + ",\"" + field + "\":" + rawJson + "}";
  }

  private ResultActions postPayment(String json) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json));
  }

  /**
   * Matches a violation by field rather than by index: the violations come from a Set, so their
   * order in the response is not stable.
   */
  private static ResultMatcher violation(String field, String message) {
    return jsonPath("$.error[?(@.field=='" + field + "')].message", hasItem(message));
  }

  private static ResultMatcher violationOn(String field) {
    return violationCountOn(field, 1);
  }

  private static ResultMatcher violationCountOn(String field, int expected) {
    return jsonPath("$.error[?(@.field=='" + field + "')]", hasSize(expected));
  }

  private void stubAuthorized() {
    UUID id = UUID.randomUUID();
    when(requestService.processPayment(any(PostPaymentRequest.class))).thenReturn(id);
    when(requestService.getPaymentById(id)).thenReturn(new PaymentResponse(
        id, PaymentStatus.AUTHORIZED, 200, "1111",
        FUTURE.getMonthValue(), FUTURE.getYear(), "GBP", 1000, null));
  }

  // --------------------------------------------------------------------- sanity

  @Test
  void validRequestIsAcceptedAndReachesTheService() throws Exception {
    stubAuthorized();

    postPayment(write(validBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment_status").value("Authorized"));

    verify(requestService).processPayment(any(PostPaymentRequest.class));
  }

  // --------------------------------------------------------------------- missing values

  @Nested
  class MissingValues {

    private String expectedMessage(String field) {
      return field.equals("currency") ? "must not be blank" : "must not be null";
    }

    @ParameterizedTest(name = "absent {0}")
    @ValueSource(strings = {"card_number", "expiry_month", "expiry_year", "currency", "amount",
        "cvv"})
    void absentFieldIsRejected(String field) throws Exception {
      postPayment(bodyWithout(field))
          .andExpect(status().isBadRequest())
          .andExpect(violation(field, expectedMessage(field)));
      verify(requestService, never()).processPayment(any());
    }

    @ParameterizedTest(name = "explicit null {0}")
    @ValueSource(strings = {"card_number", "expiry_month", "expiry_year", "currency", "amount",
        "cvv"})
    void explicitNullIsRejectedTheSameWayAsAbsent(String field) throws Exception {
      postPayment(bodyWith(field, null))
          .andExpect(status().isBadRequest())
          .andExpect(violation(field, expectedMessage(field)));
    }

    @Test
    void emptyBodyReportsEveryRequiredField() throws Exception {
      postPayment("{}")
          .andExpect(status().isBadRequest())
          .andExpect(violationOn("card_number"))
          .andExpect(violationOn("expiry_month"))
          .andExpect(violationOn("expiry_year"))
          .andExpect(violationOn("amount"))
          .andExpect(violationOn("cvv"))
          .andExpect(violationCountOn("currency", 2));
    }

    @Test
    void absentBodyIsReportedAsMalformed() throws Exception {
      mvc.perform(MockMvcRequestBuilders.post("/payment")
              .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.title").value("Malformed request"))
          .andExpect(jsonPath("$.detail").value("Request body is not valid JSON"));
    }
  }

  // --------------------------------------------------------------------- card number

  @Nested
  class CardNumber {

    @ParameterizedTest(name = "{0} digits is accepted")
    @ValueSource(ints = {14, 17, 19})
    void boundaryLengthsAreAccepted(int length) throws Exception {
      stubAuthorized();
      postPayment(bodyWith("card_number", "4".repeat(length)))
          .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} digits is rejected")
    @ValueSource(ints = {13, 20})
    void lengthsOutsideTheBoundsAreRejected(int length) throws Exception {
      postPayment(bodyWith("card_number", "4".repeat(length)))
          .andExpect(status().isBadRequest())
          .andExpect(violation("card_number", "size must be between 14 and 19"));
    }

    @ParameterizedTest(name = "non-numeric: {0}")
    @ValueSource(strings = {
        "abcdefghijklmn",
        "4111abcd1111111",
        "4111 1111 1111 111",
        "4111-1111-1111-111",
        "-411111111111111"})
    void nonNumericCardNumbersAreRejected(String cardNumber) throws Exception {
      postPayment(bodyWith("card_number", cardNumber))
          .andExpect(status().isBadRequest())
          .andExpect(violation("card_number", "must contain digits only"));
      verify(requestService, never()).processPayment(any());
    }

    @Test
    void leadingZeroIsPreservedRatherThanTreatedAsANumber() throws Exception {
      stubAuthorized();
      postPayment(bodyWith("card_number", "04111111111111"))
          .andExpect(status().isOk());
    }

    @Test
    void emptyCardNumberIsRejected() throws Exception {
      postPayment(bodyWith("card_number", ""))
          .andExpect(status().isBadRequest())
          .andExpect(violation("card_number", "size must be between 14 and 19"));
    }

    @Test
    void rejectedCardNumberIsMaskedInTheErrorBody() throws Exception {
      postPayment(bodyWith("card_number", "411111111111"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error[?(@.field=='card_number')].rejected_value",
              everyItem(is("***"))))
          .andExpect(content().string(not(containsString("411111111111"))));
    }
  }

  // --------------------------------------------------------------------- expiry

  @Nested
  class Expiry {

    @ParameterizedTest(name = "month {0} is accepted")
    @ValueSource(ints = {1, 12})
    void boundaryMonthsAreAccepted(int month) throws Exception {
      stubAuthorized();
      Map<String, Object> body = validBody();
      body.put("expiry_month", month);
      // A year far enough out that month 1 is still in the future.
      body.put("expiry_year", FUTURE.getYear() + 1);
      postPayment(write(body)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "month {0} is rejected")
    @ValueSource(ints = {0, 13, -1})
    void monthsOutsideOneToTwelveAreRejected(int month) throws Exception {
      postPayment(bodyWith("expiry_month", month))
          .andExpect(status().isBadRequest())
          .andExpect(violationOn("expiry_month"));
    }

    @Test
    void expiryInThePastIsRejected() throws Exception {
      YearMonth past = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
      Map<String, Object> body = validBody();
      body.put("expiry_month", past.getMonthValue());
      body.put("expiry_year", past.getYear());

      postPayment(write(body))
          .andExpect(status().isBadRequest())
          .andExpect(violation("request", CARD_EXPIRED));
      verify(requestService, never()).processPayment(any());
    }

    @Test
    void expiryInTheCurrentMonthIsAcceptedBecauseTheCardIsValidToMonthEnd() throws Exception {
      stubAuthorized();
      YearMonth current = YearMonth.now(ZoneOffset.UTC);
      Map<String, Object> body = validBody();
      body.put("expiry_month", current.getMonthValue());
      body.put("expiry_year", current.getYear());

      postPayment(write(body)).andExpect(status().isOk());
    }

    @Test
    void expiryNextMonthIsAccepted() throws Exception {
      stubAuthorized();
      YearMonth next = YearMonth.now(ZoneOffset.UTC).plusMonths(1);
      Map<String, Object> body = validBody();
      body.put("expiry_month", next.getMonthValue());
      body.put("expiry_year", next.getYear());

      postPayment(write(body)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "year {0} is rejected")
    @ValueSource(ints = {0, -1, 27, 999})
    void yearsThatAreNotFourDigitsAreRejected(int year) throws Exception {
      postPayment(bodyWith("expiry_year", year))
          .andExpect(status().isBadRequest())
          .andExpect(violation("expiry_year", "must be greater than or equal to 1000"));
    }

    @Test
    void anOutOfRangeYearIsReportedOnlyOnce() throws Exception {
      postPayment(bodyWith("expiry_year", 27))
          .andExpect(status().isBadRequest())
          .andExpect(violationOn("expiry_year"));
    }

    @Test
    void aNonNumericMonthIsReportedAsMalformedRatherThanAFieldViolation() throws Exception {
      postPayment(bodyWith("expiry_month", "abc"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.title").value("Malformed request"))
          .andExpect(jsonPath("$.detail").value("Invalid value for 'expiry_month'"));
    }

    @Test
    void aDecimalMonthIsReportedAsMalformed() throws Exception {
      postPayment(bodyWithRaw("expiry_month", "6.5"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Invalid value for 'expiry_month'"));
    }
  }

  // --------------------------------------------------------------------- currency

  @Nested
  class Currency {

    @ParameterizedTest(name = "{0} is accepted")
    @ValueSource(strings = {"GBP", "USD", "EUR"})
    void configuredCurrenciesAreAccepted(String currency) throws Exception {
      stubAuthorized();
      postPayment(bodyWith("currency", currency)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(strings = {"AAA", "JPY", "gbp", "123", "US", "GBPX"})
    void unsupportedCurrenciesAreRejected(String currency) throws Exception {
      postPayment(bodyWith("currency", currency))
          .andExpect(status().isBadRequest())
          .andExpect(violation("currency", "must be one of [EUR, GBP, USD]"));
      verify(requestService, never()).processPayment(any());
    }

    @Test
    void blankCurrencyIsReportedAsBothBlankAndUnsupported() throws Exception {
      postPayment(bodyWith("currency", ""))
          .andExpect(status().isBadRequest())
          .andExpect(violation("currency", "must not be blank"))
          .andExpect(violation("currency", "must be a supported currency"))
          .andExpect(violationCountOn("currency", 2));
    }
  }

  // --------------------------------------------------------------------- amount

  @Nested
  class Amount {

    @ParameterizedTest(name = "amount {0} is accepted")
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void positiveAmountsAreAccepted(int amount) throws Exception {
      stubAuthorized();
      postPayment(bodyWith("amount", amount)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "amount {0} is rejected")
    @ValueSource(ints = {0, -1})
    void nonPositiveAmountsAreRejected(int amount) throws Exception {
      postPayment(bodyWith("amount", amount))
          .andExpect(status().isBadRequest())
          .andExpect(violation("amount", "must be greater than 0"));
    }

    @ParameterizedTest(name = "amount {0} is malformed")
    @ValueSource(strings = {"10.5", "10.0", "\"abc\""})
    void nonIntegerAmountsAreRejectedBeforeValidation(String rawJsonValue) throws Exception {
      postPayment(bodyWithRaw("amount", rawJsonValue))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.title").value("Malformed request"))
          .andExpect(jsonPath("$.detail").value("Invalid value for 'amount'"));
    }

    @Test
    void anAmountThatOverflowsAnIntIsRejected() throws Exception {
      postPayment(bodyWithRaw("amount", "2147483648"))
          .andExpect(status().isBadRequest());
    }
  }

  // --------------------------------------------------------------------- cvv

  @Nested
  class Cvv {

    @ParameterizedTest(name = "cvv {0} is accepted")
    @ValueSource(strings = {"123", "1234"})
    void boundaryLengthsAreAccepted(String cvv) throws Exception {
      stubAuthorized();
      postPayment(bodyWith("cvv", cvv)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "cvv {0} is rejected")
    @ValueSource(strings = {"12", "12345", ""})
    void lengthsOutsideTheBoundsAreRejected(String cvv) throws Exception {
      postPayment(bodyWith("cvv", cvv))
          .andExpect(status().isBadRequest())
          .andExpect(violation("cvv", "size must be between 3 and 4"));
    }

    @ParameterizedTest(name = "cvv {0} is rejected")
    @ValueSource(strings = {"abc", "12a"})
    void nonNumericCvvsAreRejected(String cvv) throws Exception {
      postPayment(bodyWith("cvv", cvv))
          .andExpect(status().isBadRequest())
          .andExpect(violation("cvv", "must contain digits only"));
    }

    @Test
    void rejectedCvvIsMaskedInTheErrorBody() throws Exception {
      postPayment(bodyWith("cvv", "98765"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error[?(@.field=='cvv')].rejected_value", everyItem(is("***"))))
          .andExpect(content().string(not(containsString("98765"))));
    }
  }

  // --------------------------------------------------------------------- error contract

  @Nested
  class ErrorContract {

    @Test
    void everyInvalidFieldIsReportedInOneResponse() throws Exception {
      Map<String, Object> body = validBody();
      body.put("expiry_month", 13);
      body.put("currency", "AAA");
      body.put("amount", 0);

      postPayment(write(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error", hasSize(3)))
          .andExpect(violationOn("expiry_month"))
          .andExpect(violationOn("currency"))
          .andExpect(violationOn("amount"));
    }

    @Test
    void validationFailuresUseProblemJsonWithTheRejectedPaymentStatus() throws Exception {
      postPayment(bodyWith("amount", 0))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Validation failed"))
          .andExpect(jsonPath("$.detail").value("One or more fields are invalid"))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.payment_status").value("Rejected"));
    }

    @Test
    void violationFieldNamesUseTheWireNamesNotTheJavaOnes() throws Exception {
      postPayment(bodyWith("expiry_month", 13))
          .andExpect(violationOn("expiry_month"))
          .andExpect(jsonPath("$.error[?(@.field=='expiryMonth')]", hasSize(0)));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
      postPayment("{\"card_number\": \"4111111111111111\",")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Request body is not valid JSON"));
    }

    @Test
    void anUnknownFieldIsRejectedRatherThanSilentlyDropped() throws Exception {
      Map<String, Object> body = validBody();
      body.remove("card_number");
      body.put("card_num", VALID_CARD);

      postPayment(write(body)).andExpect(status().isBadRequest());
      verify(requestService, never()).processPayment(any());
    }

    @Test
    void anUnsupportedContentTypeIsRejectedWithA415() throws Exception {
      mvc.perform(MockMvcRequestBuilders.post("/payment")
              .contentType(MediaType.TEXT_PLAIN)
              .content("card_number=4111111111111111"))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void anUnsupportedMethodIsRejectedWithA405() throws Exception {
      mvc.perform(MockMvcRequestBuilders.delete("/payment"))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void aPathIdThatIsNotAUuidIsABadRequest() throws Exception {
      mvc.perform(MockMvcRequestBuilders.get("/payment/not-a-uuid"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Invalid value for 'id'"));
    }
  }
}
