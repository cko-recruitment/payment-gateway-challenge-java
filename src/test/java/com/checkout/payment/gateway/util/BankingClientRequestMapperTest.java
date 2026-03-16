package com.checkout.payment.gateway.util;

import com.checkout.payment.gateway.model.BankingClientRequest;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.utils.BankingClientRequestMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BankingClientRequestMapperTest {

  private final BankingClientRequestMapper mapper = new BankingClientRequestMapper();

  @Test
  void shouldMapPostPaymentRequestToBankAuthorizationRequest() {
    PaymentRequest request = new PaymentRequest();
    request.setCardNumber("4242424242424241");
    request.setExpiryMonth(12);
    request.setExpiryYear(2029);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");

    BankingClientRequest actual = mapper.map(request);

    assertEquals("4242424242424241", actual.getCardNumber());
    assertEquals("12/2029", actual.getExpiryDate());
    assertEquals("USD", actual.getCurrency());
    assertEquals(100, actual.getAmount());
    assertEquals("123", actual.getCvv());
  }

}
