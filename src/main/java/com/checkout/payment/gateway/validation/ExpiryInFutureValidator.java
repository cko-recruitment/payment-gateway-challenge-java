package com.checkout.payment.gateway.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.YearMonth;
import java.time.ZoneOffset;

public class ExpiryInFutureValidator implements ConstraintValidator<ExpiryInFuture, ExpiringCard> {

  @Override
  public boolean isValid(ExpiringCard card, ConstraintValidatorContext context) {
    if (card == null) {
      return true;
    }
    Integer month = card.expiryMonth();
    Integer year = card.expiryYear();

    // In invalid cases covered by other validators, we return true for this validator.
    if (month == null || year == null) {
      return true;
    }
    if (month < 1 || month > 12) {
      return true;
    }

    // A card is valid through the end of its expiry month, so the current month passes.
    return !YearMonth.of(year, month).isBefore(YearMonth.now(ZoneOffset.UTC));
  }
}
