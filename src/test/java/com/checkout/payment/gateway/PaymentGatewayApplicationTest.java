package com.checkout.payment.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 */
@SpringBootTest
class PaymentGatewayApplicationTest {

  /**
   * Verifies that {@link PaymentGatewayApplication#main(String[])} runs without errors.
   */
  @Test
  void mainMethodStartsApplicationContextWithoutErrors() {
    PaymentGatewayApplication.main(new String[]{"--server.port=0"});
  }
}
