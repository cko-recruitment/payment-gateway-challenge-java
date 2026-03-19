package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  private static final String VALID_PAYMENT_JSON = """
        {
          "card_number": "1234567890123456",
          "expiry_month": 12,
          "expiry_year": 2026,
          "currency": "USD",
          "amount": 1000,
          "cvv": "123"
        }
        """;

  @Autowired
  private MockMvc mvc;
  
  @Autowired
  private PaymentRepository paymentsRepository;

  @MockitoBean
  private BankSimulatorClient bankSimulatorClient;

  @Test
  void shouldReturnCorrectPaymentWhenIdExists() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse(UUID.randomUUID(), UUID.randomUUID(),
        PaymentStatus.AUTHORIZED, "4321",
        12, 2026, PaymentCurrency.USD, 10);

    paymentsRepository.save(payment);

    mvc.perform(MockMvcRequestBuilders.get("/api/payment/" + payment.paymentId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("4321"));
  }

  @Test
  void shouldProcessPaymentWhenRequestIsValid() throws Exception {
    when(bankSimulatorClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-123"));

    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();

    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .header("Idempotency-Key", paymentId.toString())
            .header("X-Merchant-Id", merchantId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("3456"))
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));
  }

  @Test
  void shouldReturnBadRequestWhenPaymentIsInvalid() throws Exception {
    String invalidJson = """
        {
          "card_number": "123",
          "expiry_month": 13,
          "expiry_year": 2020,
          "currency": "USD",
          "amount": -10,
          "cvv": "1"
        }
        """;

    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("X-Merchant-Id", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void shouldReturnBadRequestWhenCurrencyIsInvalid() throws Exception {
    String invalidCurrencyJson = VALID_PAYMENT_JSON.replace("USD", "INVALID");

    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("X-Merchant-Id", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidCurrencyJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid input format or value"));
  }

  @Test
  void shouldReturnBadRequestWhenExpiryDateIsInPast() throws Exception {
    String expiredJson = VALID_PAYMENT_JSON.replace("2026", "2020");

    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("X-Merchant-Id", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(expiredJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("Expiry date must be in the future")));
  }

  @Test
  void shouldReturnCachedResponseWhenIdempotencyKeyIsReused() throws Exception {
    when(bankSimulatorClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-123"));

    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();

    // First request
    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .header("Idempotency-Key", paymentId.toString())
            .header("X-Merchant-Id", merchantId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"));

    // Second request with same idempotency key - should return the identical response
    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .header("Idempotency-Key", paymentId.toString())
            .header("X-Merchant-Id", merchantId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"));
  }
}
