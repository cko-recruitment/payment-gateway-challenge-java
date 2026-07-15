package com.checkout.payment.gateway.observability;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityEndpointsTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private RestTemplate restTemplate;

  @Test
  void healthEndpointIncludesBankSimulatorComponent() throws Exception {
    MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
        .andExpect(jsonPath("$.components.bankSimulator.status").exists())
        .andReturn();
    int status = result.getResponse().getStatus();
    assertTrue(status == HttpStatus.OK.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value());
  }

  @Test
  void businessMetricEndpointExposesAuthorizedCounter() throws Exception {
    BankPaymentResponse bankPaymentResponse = new BankPaymentResponse();
    bankPaymentResponse.setAuthorized(true);
    bankPaymentResponse.setAuthorizationCode("code-1");
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankPaymentResponse, HttpStatus.OK));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType("application/json")
            .content("{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":2099,\"currency\":\"USD\",\"amount\":1050,\"cvv\":123}"))
        .andExpect(status().isOk());

    mvc.perform(MockMvcRequestBuilders.get("/actuator/metrics/payment.requests.authorized"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("payment.requests.authorized"));
  }
}
