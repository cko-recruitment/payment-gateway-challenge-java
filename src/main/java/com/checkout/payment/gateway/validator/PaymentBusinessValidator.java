package com.checkout.payment.gateway.validator;

import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PaymentRequest;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentBusinessValidator {

  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP");
  public void validate(PaymentRequest request) {
    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear());
    validateCurrency(request.getCurrency());
  }

  private void validateExpiryDate(Integer expiryMonth, Integer expiryYear) {
    YearMonth now = YearMonth.now();
    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);

    if (!expiry.isAfter(now) && !expiry.equals(now)) { //considering the current month is still valid
      throw new PaymentRejectedException("Card expiry date must not be in the past");
    }
  }

  private void validateCurrency(String currency) {
    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      throw new PaymentRejectedException("Unsupported currency");
    }
  }

}
