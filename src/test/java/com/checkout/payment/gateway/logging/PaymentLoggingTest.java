package com.checkout.payment.gateway.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentRequestData;
import com.checkout.payment.gateway.processor.PaymentsProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * What the application writes to its logs while it handles a payment. Card details must never reach
 * them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentLoggingTest {

  private static final String GATEWAY_LOGGER = "com.checkout.payment.gateway";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final YearMonth FUTURE = YearMonth.now(ZoneOffset.UTC).plusYears(1);

  private static final String PAN = "4111111111111111";
  private static final String PAN_BEYOND_LAST_FOUR = "411111111111";
  private static final String CVV = "7395";

  @Autowired
  private MockMvc mvc;

  @MockBean
  private PaymentsProcessor paymentsProcessor;

  private Logger gatewayLogger;
  private ListAppender<ILoggingEvent> appender;
  private Level originalLevel;

  @BeforeEach
  void captureLogs() {
    gatewayLogger = (Logger) LoggerFactory.getLogger(GATEWAY_LOGGER);
    originalLevel = gatewayLogger.getLevel();
    // The chattiest setting the gateway ships with: if nothing leaks here, nothing leaks.
    gatewayLogger.setLevel(Level.DEBUG);
    appender = new ListAppender<>();
    appender.start();
    gatewayLogger.addAppender(appender);

    when(paymentsProcessor.process(any(PaymentRequestData.class)))
        .thenReturn(new BankPaymentResponse(true, "0bb07405-6d44-4b50-a14f-7ae0beff13ad"));
  }

  @AfterEach
  void releaseLogs() {
    gatewayLogger.detachAppender(appender);
    appender.stop();
    gatewayLogger.setLevel(originalLevel);
  }

  @Test
  @DisplayName("No log line carries the card number or the CVV")
  void neverLogsTheCardNumberOrTheCvv() throws Exception {
    postPayment();

    for (String message : loggedMessages()) {
      assertFalse(message.contains(PAN), () -> "A log line printed the PAN: " + message);
      assertFalse(message.contains(PAN_BEYOND_LAST_FOUR),
          () -> "A log line printed more than the last four digits: " + message);
      assertFalse(message.contains(CVV), () -> "A log line printed the CVV: " + message);
    }
  }

  @Test
  @DisplayName("A payment leaves a trail from the controller through to the stored result")
  void logsEveryStageOfAPayment() throws Exception {
    postPayment();

    String trail = String.join("\n", loggedMessages());
    assertTrue(trail.contains("Accepted a payment request"), trail);
    assertTrue(trail.contains("Submitting a payment"), trail);
    assertTrue(trail.contains("authorized"), trail);
    assertTrue(trail.contains("Stored payment"), trail);
    assertTrue(trail.contains("finished with status"), trail);
  }

  @Test
  @DisplayName("A rejected request is logged by field name, never by rejected value")
  void logsValidationFailuresWithoutTheRejectedValues() throws Exception {
    Map<String, Object> body = validBody();
    body.put("card_number", PAN + "99999");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(body)))
        .andExpect(status().isBadRequest());

    String trail = String.join("\n", loggedMessages());
    assertTrue(trail.contains("card_number"), trail);
    assertFalse(trail.contains(PAN), () -> "A log line printed the rejected PAN: " + trail);
  }

  private MvcResult postPayment() throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(validBody())))
        .andExpect(status().isOk())
        .andReturn();
  }

  private List<String> loggedMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private static Map<String, Object> validBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("card_number", PAN);
    body.put("expiry_month", FUTURE.getMonthValue());
    body.put("expiry_year", FUTURE.getYear());
    body.put("currency", "GBP");
    body.put("amount", 1000);
    body.put("cvv", CVV);
    return body;
  }
}
