package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.properties.CurrencyProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.TreeSet;

public class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {

  private final Set<String> supportedCurrencies;
  private final String message;

  public SupportedCurrencyValidator(CurrencyProperties currencyProperties) {
    this.supportedCurrencies = Set.copyOf(currencyProperties.supportedCurrencies());
    this.message = "must be one of " + new TreeSet<>(this.supportedCurrencies);
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return false;
    }
    if (supportedCurrencies.contains(value)) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    return false;
  }
}
