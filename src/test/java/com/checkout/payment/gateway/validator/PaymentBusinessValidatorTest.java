package com.checkout.payment.gateway.validator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PaymentBusinessValidatorTest {

  private PaymentBusinessValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PaymentBusinessValidator();
  }

  @Test
  void shouldPassForValidRequest() {
    PaymentRequest request = validRequest();

    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void shouldRejectNullRequest() {
    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(null)
    );

    assertEquals(1, ex.getMessages().size());
    assertTrue(ex.getMessages().contains("Payment request is required"));
  }

  @Test
  void shouldCollectMultipleValidationErrors() {
    PaymentRequest request = new PaymentRequest();
    request.setCardNumber("12ab");
    request.setExpiryMonth(13);
    request.setExpiryYear(2020);
    request.setCurrency("Us");
    request.setAmount(-1);
    request.setCvv("12ab5");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertAll(
        () -> assertTrue(ex.getMessages().contains("Card number must be between 14 and 19 digits")),
        () -> assertTrue(ex.getMessages().contains("Card number must contain only numeric characters")),
        () -> assertTrue(ex.getMessages().contains("Expiry month must be between 1 and 12")),
        () -> assertTrue(ex.getMessages().contains("Expiry year must not be in the past")),
        () -> assertTrue(ex.getMessages().contains("Amount must be greater than zero")),
        () -> assertTrue(ex.getMessages().contains("CVV must be 3 or 4 digits")),
        () -> assertTrue(ex.getMessages().contains("CVV must contain only numeric characters"))
    );
  }

  @Test
  void shouldRejectMissingCardNumber() {
    PaymentRequest request = validRequest();
    request.setCardNumber(null);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Card number is required"));
  }

  @Test
  void shouldRejectShortCardNumber() {
    PaymentRequest request = validRequest();
    request.setCardNumber("1234567890123");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Card number must be between 14 and 19 digits"));
  }

  @Test
  void shouldRejectLongCardNumber() {
    PaymentRequest request = validRequest();
    request.setCardNumber("12345678901234567890");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Card number must be between 14 and 19 digits"));
  }

  @Test
  void shouldRejectNonNumericCardNumber() {
    PaymentRequest request = validRequest();
    request.setCardNumber("12345678901234ab");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Card number must contain only numeric characters"));
  }

  @Test
  void shouldRejectNullExpiryMonth() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(null);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Expiry month is required"));
  }

  @Test
  void shouldRejectInvalidExpiryMonthLow() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(0);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Expiry month must be between 1 and 12"));
  }

  @Test
  void shouldRejectInvalidExpiryMonthHigh() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(13);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Expiry month must be between 1 and 12"));
  }

  @Test
  void shouldRejectNullExpiryYear() {
    PaymentRequest request = validRequest();
    request.setExpiryYear(null);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Expiry year is required"));
  }

  @Test
  void shouldRejectPastExpiryDate() {
    PaymentRequest request = validRequest();
    request.setExpiryMonth(1);
    request.setExpiryYear(2020);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(
        ex.getMessages().contains("Expiry year must not be in the past")
            || ex.getMessages().contains("Card expiry date must not be in the past")
    );
  }

  @Test
  void shouldRejectMissingCurrency() {
    PaymentRequest request = validRequest();
    request.setCurrency(null);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Currency is required"));
  }

  @Test
  void shouldRejectInvalidCurrencyFormat() {
    PaymentRequest request = validRequest();
    request.setCurrency("Us");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Currency must be 3 characters"));
  }

  @Test
  void shouldRejectUnsupportedCurrency() {
    PaymentRequest request = validRequest();
    request.setCurrency("INR");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Unsupported currency"));
  }

  @Test
  void shouldRejectNullAmount() {
    PaymentRequest request = validRequest();
    request.setAmount(null);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Amount is required"));
  }

  @Test
  void shouldRejectZeroAmount() {
    PaymentRequest request = validRequest();
    request.setAmount(0);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Amount must be greater than zero"));
  }

  @Test
  void shouldRejectNegativeAmount() {
    PaymentRequest request = validRequest();
    request.setAmount(-10);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("Amount must be greater than zero"));
  }

  @Test
  void shouldRejectMissingCvv() {
    PaymentRequest request = validRequest();
    request.setCvv(null);

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("CVV is required"));
  }

  @Test
  void shouldRejectShortCvv() {
    PaymentRequest request = validRequest();
    request.setCvv("12");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("CVV must be 3 or 4 digits"));
  }

  @Test
  void shouldRejectLongCvv() {
    PaymentRequest request = validRequest();
    request.setCvv("12345");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("CVV must be 3 or 4 digits"));
  }

  @Test
  void shouldRejectNonNumericCvv() {
    PaymentRequest request = validRequest();
    request.setCvv("12a");

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> validator.validate(request)
    );

    assertTrue(ex.getMessages().contains("CVV must contain only numeric characters"));
  }

  private PaymentRequest validRequest() {
    PaymentRequest request = new PaymentRequest();
    request.setCardNumber("4242424242424241");
    request.setExpiryMonth(12);
    request.setExpiryYear(2029);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

}
