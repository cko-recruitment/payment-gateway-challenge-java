package com.checkout.payment.gateway.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  private RestTemplate restTemplate;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenValidPaymentThenAuthorizedResponseIsReturned() throws Exception {
    BankPaymentResponse bankPaymentResponse = new BankPaymentResponse();
    bankPaymentResponse.setAuthorized(true);
    bankPaymentResponse.setAuthorizationCode(UUID.randomUUID().toString());
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankPaymentResponse, HttpStatus.OK));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json")
            .content("{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void whenValidPaymentThenDeclinedResponseIsReturned() throws Exception {
    BankPaymentResponse bankPaymentResponse = new BankPaymentResponse();
    bankPaymentResponse.setAuthorized(false);
    bankPaymentResponse.setAuthorizationCode("");
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankPaymentResponse, HttpStatus.OK));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json")
            .content("{\"card_number\":\"2222405343248878\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"EUR\",\"amount\":2050,\"cvv\":999}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.DECLINED.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8878))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.amount").value(2050))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void whenPaymentRequestIsInvalidThenRejectedResponseIsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json")
            .content("{\"card_number\":\"1234\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  void whenBankIsUnavailableThen503IsReturned() throws Exception {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json")
            .content("{\"card_number\":\"2222405343248870\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Acquiring bank unavailable"));
  }
}
