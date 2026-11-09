package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.properties.CurrencyProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {

  private static final Logger LOG = LoggerFactory.getLogger(SupportedCurrencyValidator.class);

  private final Set<String> supportedCurrencies;
  private final String message;

  public SupportedCurrencyValidator(CurrencyProperties currencyProperties) {
    this.supportedCurrencies = Set.copyOf(currencyProperties.supportedCurrencies());
    this.message = "must be one of " + new TreeSet<>(this.supportedCurrencies);
    LOG.info("Accepting payments in {}", new TreeSet<>(this.supportedCurrencies));
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return false;
    }
    if (supportedCurrencies.contains(value)) {
      return true;
    }
    LOG.debug("Rejecting a payment in an unsupported currency");
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    return false;
  }
}
