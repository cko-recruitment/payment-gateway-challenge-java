package com.checkout.payment.gateway.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.model.internal.BankResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.BankClient;
import com.checkout.payment.gateway.testutils.PostPaymentRequestObjectNodeBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
  PaymentsRepository paymentsRepository;
  @MockBean
  private BankClient bankClient;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PaymentResponse payment = new PaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

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
        .andExpect(jsonPath("$.message").value("Payment does not exist"));
  }

  @Test
  void whenBankAuthorisesThenResponseIsAuthorized() throws Exception {
    ObjectNode validRequestNode = PostPaymentRequestObjectNodeBuilder.validRequest();
    String cardNumber = validRequestNode.get("card_number").toString();
    String expectedLast4 = cardNumber.substring(cardNumber.length() - 5, cardNumber.length() - 1);

    var bankResponse = new BankResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-123");
    when(bankClient.processPayment(any()))
        .thenReturn(bankResponse);

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequestNode.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(expectedLast4))
        .andExpect(jsonPath("$.expiryMonth").value(validRequestNode.get("expiry_month").toString()))
        .andExpect(jsonPath("$.expiryYear").value(validRequestNode.get("expiry_year").toString()))
        .andExpect(jsonPath("$.currency").value(validRequestNode.get("currency").toString().replace("\"", "")))
        .andExpect(jsonPath("$.amount").value(validRequestNode.get("amount").toString()));
  }

  @Test
  void whenBankDeclinesThenResponseIsDeclined() throws Exception {
    ObjectNode validRequestNode = PostPaymentRequestObjectNodeBuilder.validRequest();
    String cardNumber = validRequestNode.get("card_number").toString();
    String expectedLast4 = cardNumber.substring(cardNumber.length() - 5, cardNumber.length() - 1);

    var bankResponse = new BankResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("auth-123");
    when(bankClient.processPayment(any()))
        .thenReturn(bankResponse);

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequestNode.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value(PaymentStatus.DECLINED.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(expectedLast4))
        .andExpect(jsonPath("$.expiryMonth").value(validRequestNode.get("expiry_month").toString()))
        .andExpect(jsonPath("$.expiryYear").value(validRequestNode.get("expiry_year").toString()))
        .andExpect(jsonPath("$.currency").value(validRequestNode.get("currency").toString().replace("\"", "")))
        .andExpect(jsonPath("$.amount").value(validRequestNode.get("amount").toString()));
  }

  @ParameterizedTest
  @MethodSource("invalidPayments")
  void whenPaymentRequestInvalidThenRejectedIsReturned(ObjectNode invalidPaymentJson) throws Exception {
    mvc.perform(post("/payment")
            .content(invalidPaymentJson.toString())
            .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.getName()));
  }

  public static Stream<ObjectNode> invalidPayments() {
    int lastYear = java.time.Year.now().minusYears(1).getValue();

    return Stream.of(
        // invalid cards
        new PostPaymentRequestObjectNodeBuilder().withCardNumber(null).build(),
        new PostPaymentRequestObjectNodeBuilder().withCardNumber("nan").build(),
        new PostPaymentRequestObjectNodeBuilder().withCardNumber("1234567123456").build(),
        new PostPaymentRequestObjectNodeBuilder().withCardNumber("01234567890123456789").build(),
        // invalidExpiryMonths
        new PostPaymentRequestObjectNodeBuilder().withExpiryMonth(null).build(),
        new PostPaymentRequestObjectNodeBuilder().withExpiryMonth(0).build(),
        new PostPaymentRequestObjectNodeBuilder().withExpiryMonth(13).build(),
        // invalidExpiryYears
        new PostPaymentRequestObjectNodeBuilder().withExpiryYear(null).build(),
        new PostPaymentRequestObjectNodeBuilder().withExpiryYear(0).build(),
        new PostPaymentRequestObjectNodeBuilder().withExpiryYear(lastYear).build(), //does not cover worse case possible
        // invalidCurrency
        new PostPaymentRequestObjectNodeBuilder().withCurrency(null).build(),
        new PostPaymentRequestObjectNodeBuilder().withCurrency("NOT").build(),
        new PostPaymentRequestObjectNodeBuilder().withCurrency("LONG").build(),
        // invalidAmount
        new PostPaymentRequestObjectNodeBuilder().withAmount(null).build(),
        new PostPaymentRequestObjectNodeBuilder().withAmount(0.1).build(),
        // invalid cvv
        new PostPaymentRequestObjectNodeBuilder().withCvv(null).build(),
        new PostPaymentRequestObjectNodeBuilder().withCvv("nan").build(),
        new PostPaymentRequestObjectNodeBuilder().withCvv("12").build(),
        new PostPaymentRequestObjectNodeBuilder().withCvv("12345").build()
    );
  }

}
