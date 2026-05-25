package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentGatewayController.class)
class PaymentGatewayControllerWebTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private PaymentGatewayService paymentGatewayService;

  @TestConfiguration
  static class TestConfig {

    @Bean
    public RestTemplateBuilder restTemplateBuilder() {
      return new RestTemplateBuilder();
    }
  }

  @Test
  void shouldReturnPaymentResponseForValidRequest() throws Exception {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(java.util.UUID.randomUUID());
    response.setStatus(PaymentStatus.AUTHORIZED);
    int futureYear = YearMonth.now().getYear() + 1;
    response.setCardNumberLastFour(4242);
    response.setExpiryMonth(12);
    response.setExpiryYear(futureYear);
    response.setCurrency("USD");
    response.setAmount(1050);

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class))).thenReturn(response);

    String requestJson = "{" +
        "\"card_number\":\"4242424242424242\"," +
        "\"expiry_month\":12," +
        "\"expiry_year\":" + futureYear + "," +
        "\"currency\":\"USD\"," +
        "\"amount\":1050," +
        "\"cvv\":\"123\"" +
        "}";

    mvc.perform(post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(4242))
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  @Test
  void shouldReturnRejectedResponseWhenValidationFails() throws Exception {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setStatus(PaymentStatus.REJECTED);
    response.setMessage("Currency must be one of: [USD, EUR, GBP]");

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class))).thenReturn(response);

    int futureYear = YearMonth.now().getYear() + 1;
    String requestJson = "{" +
        "\"card_number\":\"4242424242424242\"," +
        "\"expiry_month\":12," +
        "\"expiry_year\":" + futureYear + "," +
        "\"currency\":\"AUD\"," +
        "\"amount\":1050," +
        "\"cvv\":\"123\"" +
        "}";

    mvc.perform(post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Currency must be one of: [USD, EUR, GBP]"));
  }
}
