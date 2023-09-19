package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.*;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.RequestEvent;
import com.checkout.payment.gateway.model.ResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentGatewayServiceTest {

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    this.paymentGatewayService = new PaymentGatewayService();
  }

  @Test
  void processEventTest() {
    RequestEvent event = new RequestEvent("Test");
    ResponseEvent responseEvent = paymentGatewayService.processRequest(event, true);
    assertEquals(event.getMessage(), responseEvent.getMessage());
  }

  @Test
  void processEventThrowsExceptionTest() {
    assertThrows(EventProcessingException.class ,
        () -> paymentGatewayService.processRequest(new RequestEvent("Test"), false));
  }
}