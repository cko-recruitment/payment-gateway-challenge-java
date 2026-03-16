package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {
//
//  @Autowired
//  private MockMvc mvc;
//  @Autowired
//  InMemoryPaymentsRepository paymentsRepository;
//
//  @Test
//  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
//    PostPaymentResponse payment = new PostPaymentResponse();
//    payment.setId(UUID.randomUUID());
//    payment.setAmount(10);
//    payment.setCurrency("USD");
//    payment.setStatus(PaymentStatus.AUTHORIZED);
//    payment.setExpiryMonth(12);
//    payment.setExpiryYear(2024);
//    payment.setCardNumberLastFour("4321");
//
//    paymentsRepository.add(payment);
//
//    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
//        .andExpect(status().isOk())
//        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
//        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
//        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
//        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
//        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
//        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
//  }
//
//  @Test
//  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
//    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
//        .andExpect(status().isNotFound())
//        .andExpect(jsonPath("$.message").value("Page not found"));
//  }
}
