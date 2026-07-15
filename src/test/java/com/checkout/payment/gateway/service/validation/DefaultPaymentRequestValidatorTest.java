package com.checkout.payment.gateway.service.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentRequest;
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
