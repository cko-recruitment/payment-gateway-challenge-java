package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceUnitTest {

  @Mock
  private RestTemplate restTemplate;

  private PaymentGatewayService service;
  private PaymentsRepository paymentsRepository;

  @BeforeEach
  void setUp() {
    paymentsRepository = new PaymentsRepository();
    service = new PaymentGatewayService(paymentsRepository, new IdempotencyRepository());
    service.setRestTemplate(restTemplate);
    service.setBankPaymentsUrl("http://localhost:8080/payments");
  }

  @Test
  void whenRequestIsInvalidThenPaymentIsRejectedWithoutCallingBank() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234");

    PostPaymentResponse response = service.processPaymentDetails(request);

    assertEquals(PaymentStatus.REJECTED, response.getStatus());
    verifyNoInteractions(restTemplate);
  }

  @Test
  void whenRequestIsValidThenAuthorizedPaymentIsPersisted() {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedBankResponse(), HttpStatus.OK));

    PostPaymentResponse response = service.processPaymentDetails(validRequest());

    assertNotNull(response.getId());
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals(response.getId(), paymentsRepository.get(response.getId()).orElseThrow().getId());
  }

  @Test
  void whenSameIdempotencyKeyAndSamePayloadThenBankCalledOnceAndIdReused() {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedBankResponse(), HttpStatus.OK));

    String idempotencyKey = "idem-" + UUID.randomUUID();
    PostPaymentRequest request = validRequest();
    PostPaymentResponse first = service.processPaymentDetails(request, idempotencyKey);
    PostPaymentResponse second = service.processPaymentDetails(request, idempotencyKey);

    assertEquals(first.getId(), second.getId());
    assertEquals(PaymentStatus.AUTHORIZED, second.getStatus());
    verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class));
  }

  @Test
  void whenSameIdempotencyKeyUsedWithDifferentPayloadThenConflictIsThrown() {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedBankResponse(), HttpStatus.OK));

    String idempotencyKey = "idem-" + UUID.randomUUID();
    PostPaymentRequest firstRequest = validRequest();
    PostPaymentRequest secondRequest = validRequest();
    secondRequest.setAmount(2000);
    service.processPaymentDetails(firstRequest, idempotencyKey);

    assertThrows(IdempotencyConflictException.class,
        () -> service.processPaymentDetails(secondRequest, idempotencyKey));
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2099);
    request.setCurrency("USD");
    request.setAmount(1050);
    request.setCvv(123);
    return request;
  }

  private BankPaymentResponse authorizedBankResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(true);
    response.setAuthorizationCode(UUID.randomUUID().toString());
    return response;
  }
}
