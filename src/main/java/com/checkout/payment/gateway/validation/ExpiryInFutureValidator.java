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

    if (month == null || year == null) {
      return false;
    }
    if (month < 1 || month > 12) {
      return false;
    }

    // A card is valid through the end of its expiry month, so the current month passes.
    return !YearMonth.of(year, month).isBefore(YearMonth.now(ZoneOffset.UTC));
  }
}
