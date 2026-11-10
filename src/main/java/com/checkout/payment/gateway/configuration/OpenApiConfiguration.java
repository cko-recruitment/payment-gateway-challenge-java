package com.checkout.payment.gateway.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API as a whole. Per-endpoint and per-field documentation lives on the controller
 * and on the model records.
 */
@Configuration
public class OpenApiConfiguration {

  private static final String DESCRIPTION = """
      Takes card payments from merchants and settles them through the acquiring bank.

      The gateway does two things:

      - **`POST /payment`** validates a card payment, sends it to the acquiring bank and returns
        the bank's decision synchronously. Every request that reaches the bank is stored, whether
        it was authorized or declined.
      - **`GET /payment/{id}`** returns a previously processed payment by its gateway ID.

      ### Request and response format

      Both endpoints speak JSON with `snake_case` field names. Card details are never echoed back:
      a response carries only the last four digits of the card number, and a rejected value is
      masked to `***` in validation errors.

      ### Status codes

      A payment the bank *declines* is still a successful request: it returns **200** with
      `payment_status: "Declined"`. Only a request the gateway or the bank could not process
      returns a 4xx or 5xx, as `application/problem+json`.

      | Status | Meaning |
      |--------|---------|
      | 200 | The bank reached a decision: `Authorized` or `Declined` |
      | 400 | The request failed validation, or the body could not be read |
      | 404 | No payment is held under that ID |
      | 405 | The HTTP method is not supported on this path |
      | 415 | The `Content-Type` is not `application/json` |
      | 502 | The acquiring bank rejected the request or could not be reached |
      | 503 | The acquiring bank is unavailable |

      ### Trying it out against the bank simulator

      The simulator bundled with this project decides on the **last digit of the card number**:

      | Card number ends in | Outcome |
      |---------------------|---------|
      | 1, 3, 5, 7, 9 | 200 `Authorized` |
      | 2, 4, 6, 8 | 200 `Declined` |
      | 0 | 503, the bank is unavailable |

      The examples below use `2222405343248877` (authorized) and `2222405343248112` (declined).
      """;

  @Bean
  public OpenAPI paymentGatewayOpenApi(@Value("${server.port:8090}") int port) {
    return new OpenAPI()
        .info(new Info()
            .title("Payment Gateway API")
            .version("v1")
            .description(DESCRIPTION)
            .contact(new Contact().name("Payment Gateway team"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(List.of(
            new Server().url("http://localhost:" + port).description("Local instance")));
  }

  /**
   * Makes the generated schemas name their properties the way the API actually serialises them.
   * Without this the schemas are camelCase while the wire format is snake_case, so a generated
   * client would send fields the gateway rejects.
   */
  @Bean
  public ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper);
  }

  /**
   * Drops the {@code properties} entry {@code ProblemDetail} contributes to the error schema.
   * Jackson spreads those across the body as an any-getter, so no response ever carries a field
   * by that name and documenting one would send readers looking for something that is not there.
   */
  @Bean
  public OpenApiCustomizer hideProblemDetailProperties() {
    return openApi -> {
      Schema<?> errorSchema = openApi.getComponents().getSchemas().get("ErrorResponse");
      if (errorSchema != null && errorSchema.getProperties() != null) {
        errorSchema.getProperties().remove("properties");
      }
    };
  }
}
