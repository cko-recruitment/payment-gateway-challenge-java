package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.Client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankingClientResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private PaymentsRepository paymentRepository;

  @MockBean
  private AcquiringBankClient acquiringBankClient;

  @BeforeEach
  void setUp() {
    // If your repo supports clearing, do it here.
    // Otherwise use fresh IDs and repository state carefully.
  }

  @Test
  void shouldProcessAuthorizedPayment() throws Exception {
    BankingClientResponse bankResponse = new BankingClientResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("AUTH-123");

    when(acquiringBankClient.authorize(any())).thenReturn(bankResponse);

    PaymentRequest request = validRequest("4242424242424241");

    mockMvc.perform(post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("**** **** **** 4241"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void shouldProcessDeclinedPayment() throws Exception {
    BankingClientResponse bankResponse = new BankingClientResponse();
    bankResponse.setAuthorized(false);

    when(acquiringBankClient.authorize(any())).thenReturn(bankResponse);

    PaymentRequest request = validRequest("4242424242424242");

    mockMvc.perform(post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("**** **** **** 4242"))
        .andExpect(jsonPath("$.authorizationCode").doesNotExist());
  }

  @Test
  void shouldRejectInvalidPaymentRequestWithMultipleMessages() throws Exception {
    String invalidJson = """
        {
          "cardNumber": "12ab",
          "expiryMonth": 13,
          "expiryYear": 2020,
          "currency": "Us",
          "amount": -1,
          "cvv": "12ab5"
        }
        """;

    mockMvc.perform(post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.messages").isArray())
        .andExpect(jsonPath("$.messages.length()").value(org.hamcrest.Matchers.greaterThan(1)));
  }

  @Test
  void shouldGetPaymentById() throws Exception {
    UUID paymentId = UUID.randomUUID();
    Payment payment = new Payment(
        paymentId,
        PaymentStatus.AUTHORIZED,
        "4241",
        "USD",
        100,
        12,
        2029,
        "AUTH-123",
        Instant.now()
    );

    paymentRepository.add(payment);

    mockMvc.perform(get("/api/payment/{id}", paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId.toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("4241"));
  }

  @Test
  void shouldReturn404WhenPaymentNotFound() throws Exception {
    mockMvc.perform(get("/api/payment/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  private PaymentRequest validRequest(String cardNumber) {
    PaymentRequest request = new PaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(12);
    request.setExpiryYear(2029);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
