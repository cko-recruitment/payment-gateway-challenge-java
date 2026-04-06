package com.checkout.payment.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the Jakarta Bean Validation constraints on {@link PostPaymentRequest}.
 *
 * <p>Verifies that each validation rule is correctly enforced, including card number format,
 * expiry date (month range and future date), allowed currencies, amount range, CVV range,
 * and the formatted expiry date string sent to the bank.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostPaymentRequestValidationTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  /**
   * Builds a fully valid {@link PostPaymentRequest} used as a baseline for individual constraint tests.
   *
   * @return a valid request with all fields populated correctly
   */
  private PostPaymentRequest validRequest() {
    PostPaymentRequest req = new PostPaymentRequest();
    req.setCardNumber("2222405343248877");
    req.setExpiryMonth(12);
    req.setExpiryYear(2099);
    req.setCurrency("USD");
    req.setAmount(100);
    req.setCvv(123);
    return req;
  }

  /**
   * Verifies that a fully populated request with valid values produces no constraint violations.
   */
  @Test
  @Order(1)
  void validRequestPassesValidation() {
    assertThat(validator.validate(validRequest())).isEmpty();
  }

  /**
   * Verifies that card numbers which are too short, too long, or contain non-numeric characters
   * fail validation.
   */
  @Order(2)
  @ParameterizedTest
  @ValueSource(strings = {"123", "12345678901234567890", "1234abcd5678", "abcdefghijklmno"})
  void invalidCardNumberFailsValidation(String cardNumber) {
    PostPaymentRequest req = validRequest();
    req.setCardNumber(cardNumber);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that card numbers at the exact minimum (14 digits) and maximum (19 digits)
   * boundaries pass validation.
   */
  @Order(3)
  @ParameterizedTest
  @ValueSource(strings = {"12345678901234", "1234567890123456789"})
  void validCardNumberLengthBoundariesPassValidation(String cardNumber) {
    PostPaymentRequest req = validRequest();
    req.setCardNumber(cardNumber);
    assertThat(validator.validate(req)).isEmpty();
  }

  /**
   * Verifies that a null card number fails validation due to the {@code @NotNull} constraint.
   */
  @Test
  @Order(4)
  void nullCardNumberFailsValidation() {
    PostPaymentRequest req = validRequest();
    req.setCardNumber(null);
    Set<ConstraintViolation<PostPaymentRequest>> violations = validator.validate(req);
    assertThat(violations).isNotEmpty();
  }

  /**
   * Verifies that expiry months outside the valid range of 1–12 fail validation.
   */
  @Order(5)
  @ParameterizedTest
  @ValueSource(ints = {0, 13, 99})
  void invalidExpiryMonthFailsValidation(int month) {
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(month);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that expiry months at the boundaries (1 and 12) and mid-range (6) pass validation.
   */
  @Order(6)
  @ParameterizedTest
  @ValueSource(ints = {1, 6, 12})
  void validExpiryMonthPassesValidation(int month) {
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(month);
    assertThat(validator.validate(req)).isEmpty();
  }

  /**
   * Verifies that a month/year combination clearly in the past fails the expiry date constraint.
   */
  @Test
  @Order(7)
  void pastExpiryYearMonthCombinationFailsValidation() {
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(1);
    req.setExpiryYear(2020);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that a card expiring in the current month fails validation,
   * since the expiry must be strictly after the current month.
   */
  @Test
  @Order(8)
  void currentMonthExpiryFailsValidation() {
    java.time.YearMonth now = java.time.YearMonth.now();
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(now.getMonthValue());
    req.setExpiryYear(now.getYear());
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that a card expiring next month passes the expiry date constraint.
   */
  @Test
  @Order(9)
  void futureExpiryPassesValidation() {
    java.time.YearMonth future = java.time.YearMonth.now().plusMonths(1);
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(future.getMonthValue());
    req.setExpiryYear(future.getYear());
    assertThat(validator.validate(req)).isEmpty();
  }

  /**
   * Verifies that the three allowed ISO currency codes (EUR, USD, GBP) pass validation.
   */
  @Order(12)
  @ParameterizedTest
  @ValueSource(strings = {"EUR", "USD", "GBP"})
  void allowedCurrenciesPassValidation(String currency) {
    PostPaymentRequest req = validRequest();
    req.setCurrency(currency);
    assertThat(validator.validate(req)).isEmpty();
  }

  /**
   * Verifies that currencies not in the allowed list, including lowercase variants and
   * codes shorter than 3 characters, fail validation.
   */
  @Order(13)
  @ParameterizedTest
  @ValueSource(strings = {"JPY", "AUD", "CAD", "usd", "us"})
  void disallowedOrInvalidCurrenciesFailValidation(String currency) {
    PostPaymentRequest req = validRequest();
    req.setCurrency(currency);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that a null currency fails validation due to the {@code @NotNull} constraint.
   */
  @Test
  @Order(14)
  void nullCurrencyFailsValidation() {
    PostPaymentRequest req = validRequest();
    req.setCurrency(null);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that an amount of zero or negative fails validation.
   * Amounts must be at least 1 (representing the smallest currency unit, e.g. 1 cent).
   */
  @Order(15)
  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void nonPositiveAmountFailsValidation(int amount) {
    PostPaymentRequest req = validRequest();
    req.setAmount(amount);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that the minimum valid amount of 1 passes validation.
   */
  @Test
  @Order(16)
  void positiveAmountPassesValidation() {
    PostPaymentRequest req = validRequest();
    req.setAmount(1);
    assertThat(validator.validate(req)).isEmpty();
  }

  /**
   * Verifies that CVV values below 100 (fewer than 3 digits) or above 9999 (more than 4 digits)
   * fail validation.
   */
  @Order(17)
  @ParameterizedTest
  @ValueSource(ints = {99, 10000})
  void invalidCvvRangeFailsValidation(int cvv) {
    PostPaymentRequest req = validRequest();
    req.setCvv(cvv);
    assertThat(validator.validate(req)).isNotEmpty();
  }

  /**
   * Verifies that CVV values at the 3-digit (100, 999) and 4-digit (1000, 9999) boundaries
   * all pass validation.
   */
  @Order(18)
  @ParameterizedTest
  @ValueSource(ints = {100, 999, 1000, 9999})
  void validCvvBoundariesPassValidation(int cvv) {
    PostPaymentRequest req = validRequest();
    req.setCvv(cvv);
    assertThat(validator.validate(req)).isEmpty();
  }

  /**
   * Verifies that single-digit expiry months are zero-padded in the formatted expiry date
   * string sent to the bank (e.g. month 4 becomes "04/2099").
   */
  @Test
  @Order(10)
  void expiryDateIsZeroPaddedForSingleDigitMonth() {
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(4);
    req.setExpiryYear(2099);
    assertThat(req.getExpiryDate()).isEqualTo("04/2099");
  }

  /**
   * Verifies that double-digit expiry months are formatted without modification
   * in the expiry date string (e.g. month 12 becomes "12/2099").
   */
  @Test
  @Order(11)
  void expiryDateIsFormattedCorrectlyForDoubleDigitMonth() {
    PostPaymentRequest req = validRequest();
    req.setExpiryMonth(12);
    req.setExpiryYear(2099);
    assertThat(req.getExpiryDate()).isEqualTo("12/2099");
  }

  /**
   * Verifies that {@link PostPaymentRequest#toString()} includes the key fields.
   */
  @Test
  @Order(12)
  void toStringContainsKeyFields() {
    PostPaymentRequest req = validRequest();
    String str = req.toString();
    assertThat(str).contains("PostPaymentRequest");
    assertThat(str).contains("USD");
  }

  /**
   * Verifies that all getters on {@link BankPaymentRequest} return the values passed to the constructor.
   */
  @Test
  @Order(13)
  void bankPaymentRequestGettersReturnConstructorValues() {
    BankPaymentRequest req = new BankPaymentRequest("4111111111111111", "12/2099", "USD", 100, "123");
    assertThat(req.getCardNumber()).isEqualTo("4111111111111111");
    assertThat(req.getExpiryDate()).isEqualTo("12/2099");
    assertThat(req.getCurrency()).isEqualTo("USD");
    assertThat(req.getAmount()).isEqualTo(100);
    assertThat(req.getCvv()).isEqualTo("123");
  }

  /**
   * Verifies that all getters and setters on {@link BankPaymentResponse} work correctly.
   */
  @Test
  @Order(14)
  void bankPaymentResponseGettersAndSettersWork() {
    java.util.UUID code = java.util.UUID.randomUUID();
    BankPaymentResponse resp = new BankPaymentResponse();
    resp.setAuthorized(true);
    resp.setAuthorizationCode(code);
    assertThat(resp.isAuthorized()).isTrue();
    assertThat(resp.getAuthorizationCode()).isEqualTo(code);
  }

  /**
   * Verifies that {@link ErrorResponse#toString()} includes the message field.
   */
  @Test
  @Order(15)
  void errorResponseToStringContainsMessage() {
    ErrorResponse err = new ErrorResponse("something went wrong");
    assertThat(err.toString()).contains("something went wrong");
    assertThat(err.getMessage()).isEqualTo("something went wrong");
  }

  /**
   * Verifies that {@link PostPaymentResponse#toString()} includes the key fields.
   */
  @Test
  @Order(16)
  void postPaymentResponseToStringContainsFields() {
    PostPaymentResponse resp = new PostPaymentResponse();
    resp.setId(java.util.UUID.randomUUID());
    resp.setStatus(com.checkout.payment.gateway.enums.PaymentStatus.AUTHORIZED);
    resp.setCurrency("USD");
    resp.setAmount(100);
    assertThat(resp.toString()).contains("PostPaymentResponse");
    assertThat(resp.toString()).contains("USD");
  }

  /**
   * Verifies that {@link com.checkout.payment.gateway.repository.PaymentsRepository}
   * stores and retrieves payments correctly.
   */
  @Test
  @Order(17)
  void paymentsRepositoryStoresAndRetrievesPayment() {
    com.checkout.payment.gateway.repository.PaymentsRepository repo =
        new com.checkout.payment.gateway.repository.PaymentsRepository();
    PostPaymentResponse payment = new PostPaymentResponse();
    java.util.UUID id = java.util.UUID.randomUUID();
    payment.setIdempotencyKey(id);
    payment.setStatus(com.checkout.payment.gateway.enums.PaymentStatus.AUTHORIZED);

    repo.add(payment);

    assertThat(repo.get(id)).isPresent().contains(payment);
    assertThat(repo.get(java.util.UUID.randomUUID())).isEmpty();
  }
}
