package com.checkout.payment.gateway.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PostPaymentRequestTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void closeValidator() {
    FACTORY.close();
  }

  private PostPaymentRequest request(Integer month, Integer year) {
    return new PostPaymentRequest("12345678901234", month, year, "GBP", 1, "123");
  }

  @ParameterizedTest
  @CsvSource({"-1, false", "0, false", "1, true"})
  void expiryMustBeAfterCurrentMonth(int monthsFromNow, boolean valid) {
    YearMonth expiry = YearMonth.now(ZoneOffset.UTC).plusMonths(monthsFromNow);
    PostPaymentRequest request = request(expiry.getMonthValue(), expiry.getYear());

    assertEquals(valid, VALIDATOR.validate(request).isEmpty());
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
      12, 2147483647
      12, 0
      ,   2099
      12,
      0,  2099
      13, 2099
      """)
  void invalidExpiryIsRejected(Integer month, Integer year) {
    assertFalse(VALIDATOR.validate(request(month, year)).isEmpty());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GBP  | true
      EUR  | true
      INR  | true
      USD  | false
      gbp  | false
           | false
      """)
  void validatesCurrency(String currency, boolean valid) {
    PostPaymentRequest request =
        new PostPaymentRequest("12345678901234", 1, 2099, currency, 1, "123");

    assertEquals(valid, VALIDATOR.validateProperty(request, "currency").isEmpty());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      12345678901234       | true
      1234567890123456789  | true
      1234567890123        | false
      12345678901234567890 | false
      1234567890123a       | false
      ''                  | false
                          | false
      """)
  void validatesCardNumber(String cardNumber, boolean valid) {
    PostPaymentRequest request =
        new PostPaymentRequest(cardNumber, 1, 2099, "GBP", 1, "123");

    assertEquals(valid, VALIDATOR.validateProperty(request, "cardNumber").isEmpty());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      123   | true
      1234  | true
      12    | false
      12345 | false
      abc   | false
      ''    | false
            | false
      """)
  void validatesCvv(String cvv, boolean valid) {
    PostPaymentRequest request =
        new PostPaymentRequest("12345678901234", 1, 2099, "GBP", 1, cvv);

    assertEquals(valid, VALIDATOR.validateProperty(request, "cvv").isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"1, true", "2147483647, true", "0, false", "-1, false", ", false"})
  void validatesAmount(Integer amount, boolean valid) {
    PostPaymentRequest request =
        new PostPaymentRequest("12345678901234", 1, 2099, "GBP", amount, "123");

    assertEquals(valid, VALIDATOR.validateProperty(request, "amount").isEmpty());
  }

  @Test
  void requestToStringDoesNotExposeCardNumberOrCvv() {
    PostPaymentRequest request = request(4, 2099);

    assertEquals("1234", request.cardNumberLastFour());
    assertFalse(request.toString().contains(request.cardNumber()));
    assertFalse(request.toString().contains("cvv"));
  }

  @Test
  void acquirerExpiryUsesAsciiDigitsRegardlessOfServerLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("ar-EG"));
      assertEquals("04/2099", request(4, 2099).expiryDate());
    } finally {
      Locale.setDefault(original);
    }
  }
}
