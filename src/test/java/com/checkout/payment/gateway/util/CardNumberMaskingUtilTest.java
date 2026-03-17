package com.checkout.payment.gateway.util;

import com.checkout.payment.gateway.utils.CardNumberMaskingUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CardNumberMaskingUtilTest {

  @Test
  void shouldMaskCardNumberCorrectly() {
    String cardNumber = "4242424242424242";

    String masked = CardNumberMaskingUtil.mask(cardNumber);

    assertEquals("**** **** **** 4242", masked);
  }

  @Test
  void shouldReturnLast4Digits() {
    String cardNumber = "5555444433332222";

    String last4 = CardNumberMaskingUtil.last4(cardNumber);

    assertEquals("2222", last4);
  }

  @Test
  void shouldWorkWithExactlyFourDigits() {
    String cardNumber = "1234";

    String last4 = CardNumberMaskingUtil.last4(cardNumber);

    assertEquals("1234", last4);
  }

  @Test
  void shouldThrowExceptionWhenCardNumberIsNull() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class,
            () -> CardNumberMaskingUtil.last4(null));

    assertEquals("Invalid card number", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenCardNumberLessThanFourDigits() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class,
            () -> CardNumberMaskingUtil.last4("123"));

    assertEquals("Invalid card number", exception.getMessage());
  }

  @Test
  void maskShouldThrowExceptionForInvalidInput() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class,
            () -> CardNumberMaskingUtil.mask("12"));

    assertEquals("Invalid card number", exception.getMessage());
  }
}
