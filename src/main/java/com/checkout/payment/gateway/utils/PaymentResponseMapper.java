package com.checkout.payment.gateway.utils;

import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentResponseMapper {

  public PaymentResponse map(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getStatus(),
        payment.getMaskedCardNumber(),
        payment.getExpiryMonth(),
        payment.getExpiryYear(),
        payment.getCurrency(),
        payment.getAmount()
    );
  }
}
