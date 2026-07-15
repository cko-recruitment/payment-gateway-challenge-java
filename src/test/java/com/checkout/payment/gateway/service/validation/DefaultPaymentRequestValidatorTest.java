package com.checkout.payment.gateway.service.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DefaultPaymentRequestValidatorTest {

  private final DefaultPaymentRequestValidator validator = new DefaultPaymentRequestValidator();

  @Test
  void validRequestIsAccepted() {
    assertTrue(validator.isValid(validRequest()));
  }

  @Test
  void invalidCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234");
    assertFalse(validator.isValid(request));
  }

  @Test
  void unsupportedCurrencyIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("AUD");
    assertFalse(validator.isValid(request));
  }

  @Test
  void invalidExpiryMonthIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(13);
    assertFalse(validator.isValid(request));
  }

  @Test
  void expiryMonthBelowRangeIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(0);
    assertFalse(validator.isValid(request));
  }

  @Test
  void nonFutureExpiryIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryYear(2000);
    assertFalse(validator.isValid(request));
  }

  @Test
  void currentYearCurrentOrPastMonthIsRejected() {
    LocalDate now = LocalDate.now();
    PostPaymentRequest request = validRequest();
    request.setExpiryYear(now.getYear());
    request.setExpiryMonth(now.getMonthValue());
    assertFalse(validator.isValid(request));
  }

  @Test
  void invalidAmountIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setAmount(0);
    assertFalse(validator.isValid(request));
  }

  @Test
  void invalidCvvIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCvv(12);
    assertFalse(validator.isValid(request));
  }

  @Test
  void nullCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(null);
    assertFalse(validator.isValid(request));
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2099);
    request.setCurrency("USD");
    request.setAmount(1050);
    request.setCvv(123);
    return request;
  }
}
