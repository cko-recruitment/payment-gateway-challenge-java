package com.checkout.payment.gateway.util;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.utils.PaymentResponseMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaymentResponseMapperTest {

  private final PaymentResponseMapper mapper = new PaymentResponseMapper();

  @Test
  void shouldMapPaymentToResponse() {
    UUID id = UUID.randomUUID();
    Payment payment = new Payment(
        id,
        PaymentStatus.AUTHORIZED,
        "fingerPrint",
        "4241",
        "USD",
        100,
        12,
        2029,
        "AUTH-123",
        Instant.now()
    );

    PaymentResponse response = mapper.map(payment);

    assertEquals(id, response.getId());
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals("4241", response.getCardNumberLastFour());
    assertEquals(12, response.getExpiryMonth());
    assertEquals(2029, response.getExpiryYear());
    assertEquals("USD", response.getCurrency());
    assertEquals(100, response.getAmount());
  }

}
