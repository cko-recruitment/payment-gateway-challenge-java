package com.checkout.payment.gateway.health;

import java.net.Socket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BankSimulatorHealthIndicator implements HealthIndicator {

  @Value("${bank.url}")
  private String bankUrl;

  @Override
  public Health health() {
    try {
      // Extract host and port from URL like http://host:port/path
      String hostPort = bankUrl.replace("http://", "").replace("https://", "");
      String host = hostPort.split(":")[0];
      int port = Integer.parseInt(hostPort.split(":")[1].split("/")[0]);

      try (Socket socket = new Socket(host, port)) {
        return Health.up().withDetail("bank", host + ":" + port + " reachable").build();
      }
    } catch (Exception ex) {
      // Return UNKNOWN instead of DOWN so app can still start
      // Bank availability is checked at request time, not startup
      return Health.status("UNKNOWN")
          .withDetail("bank", "unreachable: " + ex.getMessage())
          .build();
    }
  }
}
