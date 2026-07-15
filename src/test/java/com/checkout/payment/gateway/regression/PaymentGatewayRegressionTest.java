package com.checkout.payment.gateway.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.PaymentGatewayService;
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
class PaymentGatewayRegressionTest {

  @Mock
  private RestTemplate restTemplate;

  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayService(new PaymentsRepository(), new IdempotencyRepository());
    service.setRestTemplate(restTemplate);
    service.setBankPaymentsUrl("http://localhost:8080/payments");
  }

  @Test
  void rejectedRequestMustNotConsumeIdempotencyKey() {
    PostPaymentRequest invalid = validRequest();
    invalid.setCardNumber("12");
    String key = "idem-" + UUID.randomUUID();

    PostPaymentResponse rejected = service.processPaymentDetails(invalid, key);
    assertEquals(PaymentStatus.REJECTED, rejected.getStatus());

    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedBankResponse(), HttpStatus.OK));
    PostPaymentResponse accepted = service.processPaymentDetails(validRequest(), key);

    assertEquals(PaymentStatus.AUTHORIZED, accepted.getStatus());
    assertNotNull(accepted.getId());
  }

  @Test
  void idempotencyKeyWithSurroundingSpacesMustReplayOriginal() {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedBankResponse(), HttpStatus.OK));

    PostPaymentRequest request = validRequest();
    PostPaymentResponse first = service.processPaymentDetails(request, "key-123");
    PostPaymentResponse second = service.processPaymentDetails(request, "  key-123  ");

    assertEquals(first.getId(), second.getId());
    verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class));
  }

  @Test
  void idempotencySignatureMustTreatCurrencyCaseInsensitively() {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedBankResponse(), HttpStatus.OK));

    String key = "idem-" + UUID.randomUUID();
    PostPaymentRequest upperCase = validRequest();
    PostPaymentRequest lowerCase = validRequest();
    lowerCase.setCurrency("usd");

    PostPaymentResponse first = service.processPaymentDetails(upperCase, key);
    PostPaymentResponse second = service.processPaymentDetails(lowerCase, key);

    assertEquals(first.getId(), second.getId());
    verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class));
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
