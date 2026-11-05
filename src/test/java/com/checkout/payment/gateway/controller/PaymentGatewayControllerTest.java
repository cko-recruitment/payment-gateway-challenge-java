package com.checkout.payment.gateway.controller;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentData;
import com.checkout.payment.gateway.processor.PaymentsProcessor;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  PaymentsProcessor paymentsProcessor;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PaymentData payment = PaymentData.createPaymentData(true, "4321", 12, 2024, "USD", 10,
        "0bb07405-6d44-4b50-a14f-7ae0beff13ad");

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.id().toString()))
        .andExpect(jsonPath("$.payment_status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.card_number_last_four").value(payment.cardNumberLastFour()))
        .andExpect(jsonPath("$.expiry_month").value(payment.expiryMonth()))
        .andExpect(jsonPath("$.expiry_year").value(payment.expiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.currency()))
        .andExpect(jsonPath("$.amount").value(payment.amount()))
        .andExpect(jsonPath("$.rejection_message").doesNotExist());
  }

  @Test
  void whenPaymentIsDeclinedThenTheDeclinedStatusIsReturned() throws Exception {
    PaymentData payment = PaymentData.createPaymentData(false, "4321", 12, 2024, "GBP", 10, null);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment_status").value(PaymentStatus.DECLINED.getName()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Payment not found"))
        .andExpect(jsonPath("$.detail").value("No payment found with ID " + id))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.payment_status").doesNotExist());
  }

  @Test
  void whenIdIsNotAUuidThen400IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Invalid value for 'id'"));
  }
}
