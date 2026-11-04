package com.checkout.payment.gateway.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BankPaymentResponseDeserializationTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void readsTheSimulatorsAuthorizedResponse() throws Exception {
    BankPaymentResponse response = objectMapper.readValue(
        "{\"authorized\":true,\"authorization_code\":\"0bb07405-6d44-4b50-a14f-7ae0beff13ad\"}",
        BankPaymentResponse.class);

    assertTrue(response.authorized());
    assertEquals("0bb07405-6d44-4b50-a14f-7ae0beff13ad", response.authorizationCode());
  }

  @Test
  void toleratesAnUnexpectedFieldFromTheBankDespiteStrictInboundParsing() throws Exception {
    BankPaymentResponse response = objectMapper.readValue(
        "{\"authorized\":false,\"authorization_code\":\"\",\"some_new_field\":\"some_value\"}",
        BankPaymentResponse.class);

    assertEquals("", response.authorizationCode());
  }
}
