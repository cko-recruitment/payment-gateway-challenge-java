package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.BankFailure;
import com.checkout.payment.gateway.service.RequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * What a client receives when the call to the bank fails.
 */
@WebMvcTest(PaymentGatewayController.class)
@TestPropertySource(properties = "payments.currencies.supported-currencies=GBP,USD,EUR")
class PaymentGatewayControllerBankFailureTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final YearMonth FUTURE = YearMonth.now(ZoneOffset.UTC).plusYears(1);

  @Autowired
  private MockMvc mvc;

  @MockBean
  private RequestService requestService;

  private static String validBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("card_number", "4111111111111111");
    body.put("expiry_month", FUTURE.getMonthValue());
    body.put("expiry_year", FUTURE.getYear());
    body.put("currency", "GBP");
    body.put("amount", 1000);
    body.put("cvv", "123");
    try {
      return JSON.writeValueAsString(body);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private ResultActions postPaymentFailingWith(BankFailure failure) throws Exception {
    when(requestService.processPayment(any()))
        .thenThrow(new BankException(failure, "Bank returned 500"));

    return mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(validBody()));
  }

  @Test
  void bankClientErrorIsReportedAsBadGateway() throws Exception {
    postPaymentFailingWith(BankFailure.BANK_REJECTED_REQUEST)
        .andExpect(status().isBadGateway())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.payment_status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.detail")
            .value("The acquiring bank rejected the request from this gateway."));
  }

  @Test
  void bankServerErrorIsReportedAsServiceUnavailable() throws Exception {
    postPaymentFailingWith(BankFailure.BANK_UNAVAILABLE)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.payment_status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.detail").value("The acquiring bank is currently unavailable."));
  }

  @Test
  void unreachableBankIsReportedAsBadGateway() throws Exception {
    postPaymentFailingWith(BankFailure.BANK_UNREACHABLE)
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.payment_status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.detail").value("The acquiring bank could not be reached."));
  }

  @Test
  @DisplayName("no payment is created, and nothing internal is surfaced")
  void failureResponseCarriesNoPaymentAndNoInternalDetail() throws Exception {
    postPaymentFailingWith(BankFailure.BANK_UNAVAILABLE)
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(content().string(not(containsString("Bank returned 500"))))
        .andExpect(content().string(not(containsString("BankException"))));
  }
}
