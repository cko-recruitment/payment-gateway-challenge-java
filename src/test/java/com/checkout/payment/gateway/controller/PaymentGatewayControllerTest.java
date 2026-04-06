package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.EventProcessingException;
import org.springframework.http.HttpStatus;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import reactor.core.publisher.Mono;

/**
 * Integration tests for {@link PaymentGatewayController}.
 *
 * <p>Verifies end-to-end HTTP behaviour for the GET and POST payment endpoints,
 * including correct response bodies, HTTP status codes, and rejection of invalid requests.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private WebTestClient webTestClient;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  PaymentGatewayService paymentGatewayService;

  /**
   * Verifies that a GET request for an existing payment ID returns 200 OK
   */
  @Test
  @Order(1)
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    UUID paymentId = UUID.randomUUID();
    payment.setId(paymentId);
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);
    payment.setMaskedCardNumber("************4321");

    when(paymentGatewayService.getPaymentById(paymentId)).thenReturn(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.masked_card_number").value(payment.getMaskedCardNumber()))
        .andExpect(jsonPath("$.expiry_month").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiry_year").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  /**
   * Verifies that a GET request for an unknown payment ID returns 404 Not Found
   * with an appropriate error message.
   */
  @Test
  @Order(2)
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    UUID unknownId = UUID.randomUUID();
    when(paymentGatewayService.getPaymentById(unknownId))
        .thenThrow(new EventProcessingException("Invalid ID", HttpStatus.NOT_FOUND));

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + unknownId))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Invalid ID"));
  }

  /**
   * Verifies that a valid POST request with a correct {@code Idempotency-Key} returns 200 OK
   * with the payment response body.
   */
  @Test
  @Order(3)
  void whenValidPaymentRequestIsSubmittedThen200IsReturned() {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(PaymentStatus.AUTHORIZED);
    response.setCardNumberLastFour(8877);
    response.setMaskedCardNumber("************8877");
    response.setExpiryMonth(12);
    response.setExpiryYear(2099);
    response.setCurrency("USD");
    response.setAmount(100);

    when(paymentGatewayService.processPayment(any(), any(String.class)))
        .thenReturn(Mono.just(response));

    webTestClient.post().uri("/payment/processPayment")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .bodyValue("""
            {
              "card_number": "2222405343248877",
              "expiry_month": 12,
              "expiry_year": 2099,
              "currency": "USD",
              "amount": 100,
              "cvv": 123
            }
            """)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.status").isEqualTo("Authorized");
  }

  /**
   * Verifies that a POST request with a card number shorter than 14 digits
   * is rejected with 422 Unprocessable Entity and a REJECTED status,
   * without calling the bank.
   */
  @Test
  @Order(3)
  void whenRequestHasInvalidCardNumberThenPaymentIsRejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment/processPayment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "1234",
                  "expiry_month": 12,
                  "expiry_year": 2099,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": 123
                }
                """))
        .andDo(print())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value("Card number must be 14-19 digits"));
  }

  /**
   * Verifies that a POST request with a currency not in the allowed list (EUR, USD, GBP)
   * is rejected with 422 Unprocessable Entity and a REJECTED status.
   */
  @Test
  @Order(4)
  void whenRequestHasInvalidCurrencyThenPaymentIsRejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment/processPayment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "2222405343248877",
                  "expiry_month": 12,
                  "expiry_year": 2099,
                  "currency": "JPY",
                  "amount": 100,
                  "cvv": 123
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value("Currency must be one of: EUR, USD, GBP"));
  }

  /**
   * Verifies that a POST request with an expiry date in the past
   * is rejected with 422 Unprocessable Entity and a REJECTED status.
   */
  @Test
  @Order(5)
  void whenRequestHasExpiredExpiryDateThenPaymentIsRejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment/processPayment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "2222405343248877",
                  "expiry_month": 1,
                  "expiry_year": 2020,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": 123
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value("Card expiry must be in the future"));
  }

  /**
   * Verifies that a POST request without an {@code Idempotency-Key} header
   * is rejected with 400 Bad Request.
   */
  @Test
  @Order(6)
  void whenIdempotencyKeyIsMissingThenBadRequestIsReturned() throws Exception {
    when(paymentGatewayService.processPayment(any(), eq(null)))
        .thenThrow(new EventProcessingException("Idempotency-Key header is required", HttpStatus.BAD_REQUEST));

    mvc.perform(MockMvcRequestBuilders.post("/payment/processPayment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "2222405343248877",
                  "expiry_month": 12,
                  "expiry_year": 2099,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": 123
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
  }

  /**
   * Verifies that a POST request with a CVV below the minimum value of 100 (i.e. fewer than 3 digits)
   * is rejected with 422 Unprocessable Entity and a REJECTED status.
   */
  @Test
  @Order(6)
  void whenRequestHasInvalidCvvThenPaymentIsRejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment/processPayment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "2222405343248877",
                  "expiry_month": 12,
                  "expiry_year": 2099,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": 12
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value("CVV must be 3 or 4 digits"));
  }
}
