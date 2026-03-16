package com.checkout.payment.gateway.utils;

import com.checkout.payment.gateway.model.BankingClientRequest;
import com.checkout.payment.gateway.model.PaymentRequest;
import org.springframework.stereotype.Component;

@Component
public class BankingClientRequestMapper {
  public BankingClientRequest map(PaymentRequest request) {
    String expiryDate = String.format("%02d/%d", request.getExpiryMonth(), request.getExpiryYear());

    return new BankingClientRequest(
        request.getCardNumber(),
        expiryDate,
        request.getCvv(),
        request.getCurrency(),
        request.getAmount()
    );
  }

}
