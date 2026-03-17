package com.checkout.payment.gateway.util;

import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.utils.PaymentRequestFingerPrintGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PaymentRequestFingerPrintGeneratorTest {

  private PaymentRequestFingerPrintGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new PaymentRequestFingerPrintGenerator();
  }

  private PaymentRequest buildRequest(
      String cardNumber,
      int expiryMonth,
      int expiryYear,
      String currency,
      int amount,
      String cvv
  ) {
    PaymentRequest request = new PaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(expiryMonth);
    request.setExpiryYear(expiryYear);
    request.setCurrency(currency);
    request.setAmount(amount);
    request.setCvv(cvv);
    return request;
  }

  @Test
  void shouldGenerateConsistentFingerprintForSameInput() {

    PaymentRequest request = buildRequest(
        "4242424242424242",
        12,
        2030,
        "USD",
        1000,
        "123"
    );

    String fp1 = generator.fingerprint(request);
    String fp2 = generator.fingerprint(request);

    assertEquals(fp1, fp2);
  }

  @Test
  void shouldGenerateDifferentFingerprintWhenInputChanges() {

    PaymentRequest request1 = buildRequest(
        "4242424242424242",
        12,
        2030,
        "USD",
        1000,
        "123"
    );

    PaymentRequest request2 = buildRequest(
        "4242424242424242",
        12,
        2030,
        "USD",
        2000, // amount changed
        "123"
    );

    String fp1 = generator.fingerprint(request1);
    String fp2 = generator.fingerprint(request2);

    assertNotEquals(fp1, fp2);
  }

  @Test
  void shouldHandleNullFieldsGracefully() {

    PaymentRequest request = buildRequest(
        null,
        12,
        2030,
        null,
        1000,
        null
    );

    String fingerprint = generator.fingerprint(request);

    assertNotNull(fingerprint);
    assertFalse(fingerprint.isEmpty());
  }

  @Test
  void shouldTrimInputFieldsBeforeHashing() {

    PaymentRequest request1 = buildRequest(
        " 4242424242424242 ",
        12,
        2030,
        " USD ",
        1000,
        " 123 "
    );

    PaymentRequest request2 = buildRequest(
        "4242424242424242",
        12,
        2030,
        "USD",
        1000,
        "123"
    );

    String fp1 = generator.fingerprint(request1);
    String fp2 = generator.fingerprint(request2);

    assertEquals(fp1, fp2);
  }

  @Test
  void shouldGenerateValidSha256Length() {

    PaymentRequest request = buildRequest(
        "4242424242424242",
        12,
        2030,
        "USD",
        1000,
        "123"
    );

    String fingerprint = generator.fingerprint(request);

    // SHA-256 hex string length = 64 characters
    assertEquals(64, fingerprint.length());
  }

  @Test
  void shouldBeCaseSensitiveForFields() {

    PaymentRequest request1 = buildRequest(
        "4242424242424242",
        12,
        2030,
        "usd",
        1000,
        "123"
    );

    PaymentRequest request2 = buildRequest(
        "4242424242424242",
        12,
        2030,
        "USD",
        1000,
        "123"
    );

    String fp1 = generator.fingerprint(request1);
    String fp2 = generator.fingerprint(request2);

    assertNotEquals(fp1, fp2);
  }

}
