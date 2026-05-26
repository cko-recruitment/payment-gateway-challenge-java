package com.checkout.payment.gateway;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@OpenAPIDefinition(
    info = @Info(
        title = "Payment Gateway API",
        version = "1.0.0",
        description = "Secure payment processing with idempotency, rate limiting, and bank integration. " +
            "All responses use HTTP 200 with outcome details in the body.",
        contact = @Contact(name = "Checkout.com Support", email = "support@checkout.com")
    ),
    servers = {
        @Server(url = "http://localhost:8090", description = "Local development")
    },
    security = @SecurityRequirement(name = "MerchantId")
)
@SecurityScheme(
    name = "MerchantId",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "Merchant-Id",
    description = "Merchant identifier provided by Checkout.com"
)
@SpringBootApplication
@ComponentScan("com.checkout.payment.gateway")
public class PaymentGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentGatewayApplication.class, args);
  }

}
