package com.checkout.payment.gateway.processor;

import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentRequestData;


public interface PaymentsProcessor {

  BankPaymentResponse process(PaymentRequestData paymentRequestData);
}
