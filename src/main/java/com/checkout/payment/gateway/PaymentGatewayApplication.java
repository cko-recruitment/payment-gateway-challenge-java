package com.checkout.payment.gateway;

import com.checkout.payment.gateway.properties.BankProperties;
import com.checkout.payment.gateway.properties.CurrencyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableConfigurationProperties({BankProperties.class, CurrencyProperties.class})
public class PaymentGatewayApplication {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(PaymentGatewayApplication.class, args);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logReady() {
    LOG.info("The payment gateway is ready to accept requests");
  }

}
