package com.checkout.payment.gateway.model.utils;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.YearMonth;

public class ExpiryDateValidator
    implements ConstraintValidator<ValidExpiryDate, PostPaymentRequest> {

  @Override
  public boolean isValid(PostPaymentRequest req, ConstraintValidatorContext ctx) {
    var expiry = YearMonth.of(req.getExpiryYear(), req.getExpiryMonth());
    return expiry.isAfter(YearMonth.now());
  }
}
