package com.checkout.payment.gateway.service.bank;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;

public interface AcquiringBankClient {
  PaymentStatus processPayment(PostPaymentRequest paymentRequest);
}
