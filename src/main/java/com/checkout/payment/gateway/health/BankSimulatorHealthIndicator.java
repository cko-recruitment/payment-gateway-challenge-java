package com.checkout.payment.gateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorHealthIndicator implements HealthIndicator {

  @Value("${bank.url}")
  private String bankUrl;

  private final RestTemplate restTemplate = new RestTemplate();

  @Override
  public Health health() {
    try {
      restTemplate.getForEntity(bankUrl, String.class);
      return Health.up().withDetail("bank", "available").build();
    } catch (Exception ex) {
      return Health.down().withDetail("bank", "unavailable: " + ex.getMessage()).build();
    }
  }
}
