package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.RejectedPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CommonExceptionHandlerTest {
  private CommonExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CommonExceptionHandler();
  }

  @Test
  void shouldHandlePaymentRejectedException() {
    PaymentRejectedException ex = new PaymentRejectedException(
        List.of("CVV is required", "Amount is required"));

    ResponseEntity<RejectedPaymentResponse> response = handler.handlePaymentRejected(ex);

    assertEquals(400, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals("Rejected", response.getBody().getStatus());
    assertEquals(2, response.getBody().getMessages().size());
  }

  @Test
  void shouldHandlePaymentNotFoundException() {
    ResponseEntity<String> response =
        handler.handlePaymentNotFound(new PaymentNotFoundException("not found"));

    assertEquals(404, response.getStatusCode().value());
    assertEquals("not found", response.getBody());
  }

  @Test
  void shouldHandleBankUnavailableException() {
    ResponseEntity<String> response =
        handler.handleBankUnavailable(new BankUnavailableException("bank unavailable"));

    assertEquals(502, response.getStatusCode().value());
    assertEquals("bank unavailable", response.getBody());
  }

}
