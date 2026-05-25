package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;

public interface BankClient {

  BankPaymentResponse processPayment(PostPaymentRequest paymentRequest);
}
