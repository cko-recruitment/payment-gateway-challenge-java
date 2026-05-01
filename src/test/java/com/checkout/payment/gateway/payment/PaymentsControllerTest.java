package com.checkout.payment.gateway.payment;


import static com.checkout.payment.gateway.TestUtils.givenPaymentResponse;
import static com.checkout.payment.gateway.TestUtils.givenValidPaymentRequest;
import static com.checkout.payment.gateway.generated.api.model.PaymentStatusEnum.AUTHORIZED;
import static java.util.Optional.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.idempotency.IdempotencyService;
import com.checkout.payment.gateway.shared.exception.PaymentNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(PaymentsController.class)
class PaymentsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PaymentsService paymentsService;

  @MockitoBean
  private IdempotencyService idempotencyService;

  @Autowired
  private JsonMapper jsonMapper;

  private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

  @Nested
  class GetEndpointScenarios {

    @Test
    void whenGetPaymentByIdShouldReturnPayment() throws Exception {
      // given
      PaymentResponse response = givenPaymentResponse();
      given(paymentsService.findById(response.getId())).willReturn(response);

      // when then
      mockMvc.perform(get("/v1/payments/{paymentId}", response.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(response.getId().toString()))
          .andExpect(jsonPath("$.status").value("Authorized"))
          .andExpect(jsonPath("$.cardNumberLastFour").value("4321"))
          .andExpect(jsonPath("$.amount").value(10));
    }

    @Test
    void whenGetPaymentByIdAndPaymentNotExistsShouldThrowPaymentNotFound() throws Exception {
      // given
      UUID paymentId = UUID.randomUUID();
      given(paymentsService.findById(paymentId)).willThrow(new PaymentNotFoundException(paymentId));

      // when then
      mockMvc.perform(get("/v1/payments/{id}", paymentId))
          .andExpect(status().isNotFound())
          .andExpect(
              jsonPath("$.message").value("Payment not found for id: %s".formatted(paymentId)));
    }

  }

  @Nested
  class PostEndpointSuccessScenarios {

    @Test
    void whenProcessPaymentWithValidRequestShouldReturnCreated() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest();
      PaymentResponse response = givenPaymentResponse();
      given(paymentsService.processPayment(request)).willReturn(response);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(response.getId().toString()))
          .andExpect(jsonPath("$.status").value(AUTHORIZED.getValue()))
          .andExpect(jsonPath("$.cardNumberLastFour").value("4321"))
          .andExpect(jsonPath("$.amount").value(10));

      then(paymentsService).should().processPayment(request);
    }

    @Test
    void whenProcessPaymentWithValidRequestShouldReturnOk() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest();
      PaymentResponse response = givenPaymentResponse();
      given(idempotencyService.findBy(IDEMPOTENCY_KEY)).willReturn(of(response));

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(response.getId().toString()))
          .andExpect(jsonPath("$.status").value(AUTHORIZED.getValue()))
          .andExpect(jsonPath("$.cardNumberLastFour").value("4321"))
          .andExpect(jsonPath("$.amount").value(10));

      then(paymentsService).should(never()).processPayment(request);
    }

  }

  @Nested
  class PostEndpointValidationScenarios {

    @Test
    void whenProcessPaymentAndCardNumberNullShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cardNumber(null);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cardNumber: must not be null"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCardNumberEmptyShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cardNumber("");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cardNumber: must be between 14 and 19 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }


    @Test
    void whenProcessPaymentAndCardNumberShortShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cardNumber("1234");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cardNumber: must be between 14 and 19 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCardNumberTooLongShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cardNumber("12345678901234567890");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cardNumber: must be between 14 and 19 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCardContainsLetterShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cardNumber("123456789012ABCD");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cardNumber: must be between 14 and 19 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndExpiryMonthInvalidShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().expiryMonth(13);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("expiryMonth: must be less than or equal to 12"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndExpiryMonthZeroShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().expiryMonth(0);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.message").value("expiryMonth: must be greater than or equal to 1"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndExpiryMonthNullShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().expiryMonth(null);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("expiryMonth: must not be null"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCurrencyNullShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().currency(null);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("currency: must not be null"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndExpiryYearNullShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().expiryYear(null);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("expiryYear: must not be null"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndExpiryYearTooLowShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().expiryYear(2025);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("expiryYear: Year cannot be in the past"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCvvNullShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cvv(null);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cvv: must not be null"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCvvEmptyShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cvv("");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cvv: must be 3 or 4 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCvvTooShortShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cvv("12");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cvv: must be 3 or 4 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCvvTooLongShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cvv("12345");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cvv: must be 3 or 4 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndCvvContainsLetterShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().cvv("12A");

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("cvv: must be 3 or 4 digits"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndAmountNullShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().amount(null);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("amount: must not be null"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndAmountNegativeShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().amount(-1);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("amount: must be greater than or equal to 1"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndAmountZeroShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest().amount(0);

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("amount: must be greater than or equal to 1"));

      then(paymentsService).should(never()).processPayment(any());
    }

    @Test
    void whenProcessPaymentAndIdempotencyKeyMissingShouldReturnBadRequest() throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest();

      // when then
      mockMvc.perform(post("/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(
              "Required request header 'Idempotency-Key' for method parameter type UUID is not present"));

      then(paymentsService).should(never()).processPayment(any());
    }

  }

  @Nested
  class PostEndpointFailingScenarios {

    @Test
    void whenProcessPaymentAndServiceThrowsExceptionShouldReturnInternalServerError()
        throws Exception {
      // given
      PaymentRequest request = givenValidPaymentRequest();
      given(paymentsService.processPayment(request))
          .willThrow(new RuntimeException("Payment processing failed"));

      // when then
      mockMvc.perform(post("/v1/payments")
              .header("Idempotency-Key", IDEMPOTENCY_KEY)
              .contentType(MediaType.APPLICATION_JSON)
              .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

      then(paymentsService).should().processPayment(request);
    }

  }
}