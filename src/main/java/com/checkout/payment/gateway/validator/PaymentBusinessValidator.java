package com.checkout.payment.gateway.validator;

import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PaymentRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
/**
 * Component responsible for performing business-level validation on {@link PaymentRequest}.

 * This validator ensures that all incoming payment requests conform to expected
 * business rules before being processed. It performs comprehensive validation across
 * multiple fields and aggregates all validation errors before rejecting the request.

 * Validation Strategy:
    - Validate all fields and collect errors instead of failing fast
    - Throw a {@link PaymentRejectedException} containing all validation errors
    - Ensure both format and business rules are enforced

 * Validated Fields:
    - Card number (presence, length, numeric format)
    - Expiry month and year (valid ranges and not in the past)
    - Currency (format and supported values)
    - Amount (non-null and greater than zero)
    - CVV (length and numeric format)

 * Error Handling:
 * If validation fails, a {@link PaymentRejectedException} is thrown containing
 * a list of all validation errors, enabling clients to correct multiple issues
 * in a single response.

 * Supported Currencies:
 * The validator currently supports a fixed set of currencies defined in
 * {@link #SUPPORTED_CURRENCIES}.
 */
@Component
public class PaymentBusinessValidator {

  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP");

  public void validate(PaymentRequest request) {
    List<String> errors = new ArrayList<>();

    if (request == null) {
      errors.add("Payment request is required");
      throw new PaymentRejectedException(errors);
    }

    boolean cardNumberValid = validateCardNumber(request.getCardNumber(), errors);
    boolean expiryMonthValid = validateExpiryMonth(request.getExpiryMonth(), errors);
    boolean expiryYearValid = validateExpiryYear(request.getExpiryYear(), errors);
    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear(), expiryMonthValid, expiryYearValid, errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);

    if (!errors.isEmpty()) {
      throw new PaymentRejectedException(errors);
    }
  }

  private boolean validateCardNumber(String cardNumber, List<String> errors) {
    boolean valid = true;

    if (!StringUtils.hasText(cardNumber)) {
      errors.add("Card number is required");
      return false;
    }

    if (cardNumber.length() < 14 || cardNumber.length() > 19) {
      errors.add("Card number must be between 14 and 19 digits");
      valid = false;
    }

    if (!cardNumber.matches("^\\d+$")) {
      errors.add("Card number must contain only numeric characters");
      valid = false;
    }

    return valid;
  }

  private boolean validateExpiryMonth(Integer expiryMonth, List<String> errors) {
    if (expiryMonth == null) {
      errors.add("Expiry month is required");
      return false;
    }

    if (expiryMonth < 1 || expiryMonth > 12) {
      errors.add("Expiry month must be between 1 and 12");
      return false;
    }

    return true;
  }

  private boolean validateExpiryYear(Integer expiryYear, List<String> errors) {
    if (expiryYear == null) {
      errors.add("Expiry year is required");
      return false;
    }

    int currentYear = YearMonth.now().getYear();
    if (expiryYear < currentYear) {
      errors.add("Expiry year must not be in the past");
      return false;
    }

    return true;
  }

  private void validateExpiryDate(Integer expiryMonth,
      Integer expiryYear,
      boolean expiryMonthValid,
      boolean expiryYearValid,
      List<String> errors) {
    if (!expiryMonthValid || !expiryYearValid) {
      return;
    }

    YearMonth now = YearMonth.now();
    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);

    if (expiry.isBefore(now)) {
      errors.add("Card expiry date must not be in the past");
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (!StringUtils.hasText(currency)) {
      errors.add("Currency is required");
      return;
    }

    boolean validFormat = true;

    if (currency.length() != 3) {
      errors.add("Currency must be 3 characters");
      validFormat = false;
    }

    if (!currency.matches("^[A-Z]{3}$")) {
      errors.add("Currency must contain only uppercase alphabetic characters");
      validFormat = false;
    }

    if (validFormat && !SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add("Unsupported currency");
    }
  }

  private void validateAmount(Integer amount, List<String> errors) {
    if (amount == null) {
      errors.add("Amount is required");
      return;
    }

    if (amount <= 0) {
      errors.add("Amount must be greater than zero");
    }
  }

  private void validateCvv(String cvv, List<String> errors) {
    if (!StringUtils.hasText(cvv)) {
      errors.add("CVV is required");
      return;
    }

    if (cvv.length() < 3 || cvv.length() > 4) {
      errors.add("CVV must be 3 or 4 digits");
    }

    if (!cvv.matches("^\\d+$")) {
      errors.add("CVV must contain only numeric characters");
    }
  }
}