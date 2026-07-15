package com.checkout.payment.gateway.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class BankSimulatorHealthIndicatorTest {

  @Test
  void healthIsUpWhenBankHostIsReachable() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      BankSimulatorHealthIndicator indicator = new BankSimulatorHealthIndicator();
      indicator.setBankPaymentsUrl("http://localhost:" + serverSocket.getLocalPort() + "/payments");

      assertEquals("UP", indicator.health().getStatus().getCode());
    }
  }

  @Test
  void healthIsDownWhenBankHostIsUnreachable() throws IOException {
    int freePort;
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      freePort = serverSocket.getLocalPort();
    }

    BankSimulatorHealthIndicator indicator = new BankSimulatorHealthIndicator();
    indicator.setBankPaymentsUrl("http://localhost:" + freePort + "/payments");

    assertEquals("DOWN", indicator.health().getStatus().getCode());
  }
}
