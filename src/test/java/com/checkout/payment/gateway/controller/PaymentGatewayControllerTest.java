package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;

  @Test
  void simpleGetRequest() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/ok").param("message", "test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("test"));
  }

  @Test
  void simpleGetRequestException() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/nok").param("message", "test"))
        .andExpect(status().is(500))
        .andExpect(jsonPath("$.message").value("Something happened"));
  }
}
