package com.checkout.payment.gateway.service.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DefaultPaymentRequestValidator implements PaymentRequestValidator {
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP");

  @Override
  public boolean isValid(PostPaymentRequest paymentRequest) {
    if (paymentRequest.getCardNumber() == null || !paymentRequest.getCardNumber().matches("^\\d{14,19}$")) {
      return false;
    }
    if (paymentRequest.getExpiryMonth() < 1 || paymentRequest.getExpiryMonth() > 12) {
      return false;
    }
    LocalDate now = LocalDate.now();
    if (paymentRequest.getExpiryYear() < now.getYear()) {
      return false;
    }
    if (paymentRequest.getExpiryYear() == now.getYear()
        && paymentRequest.getExpiryMonth() <= now.getMonthValue()) {
      return false;
    }
    String currency = normalizeCurrency(paymentRequest.getCurrency());
    if (currency == null || currency.length() != 3 || !SUPPORTED_CURRENCIES.contains(currency)) {
      return false;
    }
    if (paymentRequest.getAmount() <= 0) {
      return false;
    }
    String cvv = String.valueOf(paymentRequest.getCvv());
    return cvv.matches("^\\d{3,4}$");
  }

  private String normalizeCurrency(String currency) {
    if (currency == null) {
      return null;
    }
    return currency.trim().toUpperCase();
  }
}
