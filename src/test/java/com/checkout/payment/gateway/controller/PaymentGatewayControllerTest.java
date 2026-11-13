package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.acquirer.AcquirerClient;
import com.checkout.payment.gateway.exception.AcquirerException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  private static final String VALID_REQUEST = """
      {
        "card_number": "12345678900001",
        "expiry_month": 12,
        "expiry_year": 2099,
        "currency": "GBP",
        "amount": 10,
        "cvv": "123"
      }
      """;

  @Autowired
  MockMvc mvc;
  @MockBean
  AcquirerClient acquirer;

  @ParameterizedTest
  @CsvSource({"true, Authorized", "false, Declined"})
  void createsAndRetrievesPayment(boolean authorized, String expectedStatus) throws Exception {
    when(acquirer.authorize(any())).thenReturn(authorized);

    String location = mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REQUEST))
        .andExpect(status().isCreated())
        .andExpect(header().exists("X-Request-ID"))
        .andExpect(jsonPath("$.status").value(expectedStatus))
        .andExpect(jsonPath("$.card_number_last_four").value("0001"))
        .andExpect(jsonPath("$.card_number").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist())
        .andReturn().getResponse().getHeader("Location");

    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(expectedStatus))
        .andExpect(jsonPath("$.card_number_last_four").value("0001"))
        .andExpect(jsonPath("$.expiry_month").value(12))
        .andExpect(jsonPath("$.expiry_year").value(2099))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(10))
        .andExpect(jsonPath("$.card_number").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist());
    verify(acquirer).authorize(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}", "{",
      """
          {
            "card_number": "12345678900001",
            "expiry_month": 12,
            "expiry_year": 2099,
            "currency": "GBP",
            "amount": 1.5,
            "cvv": "123"
          }
          """,
      """
          {
            "card_number": "12345678900001",
            "expiry_month": 12,
            "expiry_year": 2099,
            "currency": "GBP",
            "amount": 2147483648,
            "cvv": "123"
          }
          """
  })
  void invalidRequestNeverCallsAcquirer(String body) throws Exception {
    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists("X-Request-ID"))
        .andExpect(jsonPath("$.status").value("Rejected"));
    verifyNoInteractions(acquirer);
  }

  @Test
  void unknownPaymentReturnsNotFound() throws Exception {
    mvc.perform(get("/payments/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void malformedIdReturnsBadRequest() throws Exception {
    mvc.perform(get("/payments/not-a-uuid")).andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({"true, 503", "false, 502"})
  void acquirerFailureReturnsError(boolean unavailable, int expectedStatus) throws Exception {
    when(acquirer.authorize(any())).thenThrow(new AcquirerException(unavailable));

    mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.status").doesNotExist());
    verify(acquirer).authorize(any());
  }

  @Test
  void exposesHealthProbesAndHttpMetricsOnly() throws Exception {
    for (String endpoint : new String[]{"health", "health/liveness", "health/readiness"}) {
      mvc.perform(get("/actuator/" + endpoint))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"))
          .andExpect(jsonPath("$.components").doesNotExist());
    }
    mvc.perform(get("/payments/" + UUID.randomUUID())).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/metrics/http.server.requests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("http.server.requests"));
    mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
  }

}
