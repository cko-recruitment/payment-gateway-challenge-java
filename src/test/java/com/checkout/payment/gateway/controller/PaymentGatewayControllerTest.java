package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
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
class PaymentGatewayControllerTest {

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
  void whenDedupeKeyExistsThenReturnExistingPayment() throws Exception {
    UUID id = UUID.randomUUID();
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(id);
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    when(paymentGatewayService.getPaymentByDedupeKey("merchant-123", "dedupe-abc"))
        .thenReturn(Optional.of(payment));

    mvc.perform(post("/api/v1/payments")
            .header("Merchant-Id", "merchant-123")
            .header("Idempotency-Key", "dedupe-abc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"card_number\":\"4242424242424242\",\"expiry_month\":12,\"expiry_year\":2025,\"currency\":\"USD\",\"amount\":10,\"cvv\":\"123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void whenGetPaymentByIdempotencyKeyExistsThenReturnPayment() throws Exception {
    UUID id = UUID.randomUUID();
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(id);
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    when(paymentGatewayService.getPaymentByDedupeKey("merchant-123", "dedupe-abc"))
        .thenReturn(Optional.of(payment));

    mvc.perform(get("/api/v1/payments/idempotency/dedupe-abc")
            .header("Merchant-Id", "merchant-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("Authorized"));
  }

  @Test
  void whenGetPaymentByIdExistsThenReturnPayment() throws Exception {
    UUID id = UUID.randomUUID();
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(id);
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    when(paymentGatewayService.getPaymentById("merchant-123", id))
        .thenReturn(Optional.of(payment));

    mvc.perform(get("/api/v1/payments/" + id)
            .header("Merchant-Id", "merchant-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("Authorized"));
  }

  @Test
  void shouldReturnPaymentResponseForValidRequest() throws Exception {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(PaymentStatus.AUTHORIZED);
    int futureYear = YearMonth.now().getYear() + 1;
    response.setCardNumberLastFour(4242);
    response.setExpiryMonth(12);
    response.setExpiryYear(futureYear);
    response.setCurrency("USD");
    response.setAmount(1050);

    when(paymentGatewayService.processPayment(anyString(), any(PostPaymentRequest.class), any()))
        .thenReturn(response);

    String requestJson = "{" +
        "\"card_number\":\"4242424242424242\"," +
        "\"expiry_month\":12," +
        "\"expiry_year\":" + futureYear + "," +
        "\"currency\":\"USD\"," +
        "\"amount\":1050," +
        "\"cvv\":\"123\"" +
        "}";

    mvc.perform(post("/api/v1/payments")
            .header("Merchant-Id", "merchant-123")
            .header("Idempotency-Key", "dedupe-123")
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
    response.setDescription("Currency must be one of: [USD, EUR, GBP]");

    when(paymentGatewayService.processPayment(anyString(), any(PostPaymentRequest.class), any()))
        .thenReturn(response);

    int futureYear = YearMonth.now().getYear() + 1;
    String requestJson = "{" +
        "\"card_number\":\"4242424242424242\"," +
        "\"expiry_month\":12," +
        "\"expiry_year\":" + futureYear + "," +
        "\"currency\":\"AUD\"," +
        "\"amount\":1050," +
        "\"cvv\":\"123\"" +
        "}";

    mvc.perform(post("/api/v1/payments")
            .header("Merchant-Id", "merchant-123")
            .header("Idempotency-Key", "dedupe-reject")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.description").value("Currency must be one of: [USD, EUR, GBP]"));
  }
}
