package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record PostPaymentResponse(UUID paymentId,
                                  UUID merchantId,
                                  PaymentStatus status,
                                  String cardNumberLastFour,
                                  int expiryMonth,
                                  int expiryYear,
                                  PaymentCurrency currency,
                                  long amount) {
  
}
