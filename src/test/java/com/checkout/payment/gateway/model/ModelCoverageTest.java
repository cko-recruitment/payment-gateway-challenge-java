package com.checkout.payment.gateway.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelCoverageTest {

  @Test
  void getPaymentResponseGettersSettersAndToStringWork() {
    GetPaymentResponse response = new GetPaymentResponse();
    UUID id = UUID.randomUUID();
    response.setId(id);
    response.setStatus(PaymentStatus.AUTHORIZED);
    response.setCardNumberLastFour(1234);
    response.setExpiryMonth(12);
    response.setExpiryYear(2099);
    response.setCurrency("USD");
    response.setAmount(1050);

    assertEquals(id, response.getId());
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals(1234, response.getCardNumberLastFour());
    assertEquals(12, response.getExpiryMonth());
    assertEquals(2099, response.getExpiryYear());
    assertEquals("USD", response.getCurrency());
    assertEquals(1050, response.getAmount());
    assertTrue(response.toString().contains("GetPaymentResponse"));
  }

  @Test
  void postPaymentRequestGettersSettersExpiryDateAndToStringWork() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setCardNumberLastFour(8877);
    request.setExpiryMonth(4);
    request.setExpiryYear(2029);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv(123);

    assertEquals("2222405343248877", request.getCardNumber());
    assertEquals(8877, request.getCardNumberLastFour());
    assertEquals(4, request.getExpiryMonth());
    assertEquals(2029, request.getExpiryYear());
    assertEquals("GBP", request.getCurrency());
    assertEquals(100, request.getAmount());
    assertEquals(123, request.getCvv());
    assertEquals("4/2029", request.getExpiryDate());
    assertTrue(request.toString().contains("PostPaymentRequest"));
  }

  @Test
  void bankPaymentRequestAndResponseAccessorsWork() {
    BankPaymentRequest bankRequest = new BankPaymentRequest("4111111111111111", "04/2029", "EUR", 1000, "123");
    assertEquals("4111111111111111", bankRequest.getCardNumber());
    assertEquals("04/2029", bankRequest.getExpiryDate());
    assertEquals("EUR", bankRequest.getCurrency());
    assertEquals(1000, bankRequest.getAmount());
    assertEquals("123", bankRequest.getCvv());

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-code");
    assertTrue(bankResponse.isAuthorized());
    assertEquals("auth-code", bankResponse.getAuthorizationCode());
  }

  @Test
  void errorResponseMessageAndToStringWork() {
    ErrorResponse errorResponse = new ErrorResponse("boom");
    assertEquals("boom", errorResponse.getMessage());
    assertTrue(errorResponse.toString().contains("boom"));
  }

  @Test
  void idempotentPaymentRecordAccessorsWork() {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    IdempotentPaymentRecord record = new IdempotentPaymentRecord("sig", response);
    assertEquals("sig", record.getRequestSignature());
    assertNotNull(record.getResponse());
  }
}
