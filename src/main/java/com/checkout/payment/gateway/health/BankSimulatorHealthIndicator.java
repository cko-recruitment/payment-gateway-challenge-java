package com.checkout.payment.gateway.health;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("bankSimulator")
public class BankSimulatorHealthIndicator implements HealthIndicator {

  private String bankPaymentsUrl = "http://localhost:8080/payments";

  @Value("${bank.simulator.url:http://localhost:8080/payments}")
  public void setBankPaymentsUrl(String bankPaymentsUrl) {
    this.bankPaymentsUrl = bankPaymentsUrl;
  }

  @Override
  public Health health() {
    URI uri = URI.create(bankPaymentsUrl);
    String host = uri.getHost();
    int port = uri.getPort();
    if (port == -1) {
      port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 1000);
      return Health.up()
          .withDetail("url", bankPaymentsUrl)
          .withDetail("host", host)
          .withDetail("port", port)
          .build();
    } catch (Exception ex) {
      return Health.down(ex)
          .withDetail("url", bankPaymentsUrl)
          .withDetail("host", host)
          .withDetail("port", port)
          .build();
    }
  }
}
