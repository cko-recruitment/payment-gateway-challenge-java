package com.checkout.payment.gateway.shared.validation;

import static java.util.Objects.isNull;

import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.YearMonth;

public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, PaymentRequest> {

  @Override
  public boolean isValid(PaymentRequest request, ConstraintValidatorContext context) {
    if (isNull(request)) {
      return true;
    }
    if (isNull(request.getExpiryYear()) || isNull(request.getExpiryMonth())) {
      return true;
    }

    if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      return true;
    }

    YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
    YearMonth now = YearMonth.now();
    if (expiry.getYear() < now.getYear()) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Year cannot be in the past")
          .addPropertyNode("expiryYear")
          .addConstraintViolation();
      return false;
    }

    if (expiry.isBefore(now)) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Card is expired")
          .addPropertyNode("expiryMonth")
          .addConstraintViolation();
      return false;
    }
    return true;
  }
}
