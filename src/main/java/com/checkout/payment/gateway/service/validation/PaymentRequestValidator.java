package com.checkout.payment.gateway.service.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;

public interface PaymentRequestValidator {
  boolean isValid(PostPaymentRequest paymentRequest);
}
