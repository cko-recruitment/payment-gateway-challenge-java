package com.checkout.payment.gateway;

import com.checkout.payment.gateway.properties.BankProperties;
import com.checkout.payment.gateway.properties.CurrencyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BankProperties.class, CurrencyProperties.class})
public class PaymentGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentGatewayApplication.class, args);
  }

}
