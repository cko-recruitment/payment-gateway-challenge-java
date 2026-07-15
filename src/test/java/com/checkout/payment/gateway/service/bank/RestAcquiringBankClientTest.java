package com.checkout.payment.gateway.service.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RestAcquiringBankClientTest {

  @Mock
  private RestTemplate restTemplate;

  private RestAcquiringBankClient client;

  @BeforeEach
  void setUp() {
    client = new RestAcquiringBankClient(restTemplate);
    client.setBankPaymentsUrl("http://localhost:8080/payments");
  }

  @Test
  void authorizedBankResponseMapsToAuthorizedStatus() {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    assertEquals(PaymentStatus.AUTHORIZED, client.processPayment(validRequest()));
  }

  @Test
  void declinedBankResponseMapsToDeclinedStatus() {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    assertEquals(PaymentStatus.DECLINED, client.processPayment(validRequest()));
  }

  @Test
  void bankServerErrorMapsToBankUnavailable() {
    when(restTemplate.postForEntity(eq("http://localhost:8080/payments"), any(), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(BankUnavailableException.class, () -> client.processPayment(validRequest()));
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
}
