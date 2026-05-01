package com.checkout.payment.gateway.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import jakarta.validation.ConstraintValidatorContext;

import java.time.YearMonth;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryDateValidatorTest {

  private static final String CARD_EXPIRED_MESSAGE = "Card is expired";
  private static final String YEAR_IN_THE_PAST_MESSAGE = "Year cannot be in the past";
  private static final String EXPIRY_MONTH_PROPERTY = "expiryMonth";
  private static final String EXPIRY_YEAR_PROPERTY = "expiryYear";

  private final ExpiryDateValidator validator = new ExpiryDateValidator();

  @Mock
  private ConstraintValidatorContext context;

  @Mock
  private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

  @Mock
  private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

  @Nested
  class SuccessScenarios {

    @Test
    void shouldAcceptNullRequest() {
      // given when
      boolean result = validator.isValid(null, context);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptRequestWithNullExpiryYear() {
      // given
      PaymentRequest request = givenPaymentRequest(12, null);

      // when
      boolean result = validator.isValid(request, context);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptRequestWithNullExpiryMonth() {
      // given
      PaymentRequest request = givenPaymentRequest(null, YearMonth.now().getYear());

      // when
      boolean result = validator.isValid(request, context);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptRequestWithExpiryMonthBelowAllowedRange() {
      // given
      PaymentRequest request = givenPaymentRequest(0, YearMonth.now().getYear());

      // when
      boolean result = validator.isValid(request, context);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptRequestWithExpiryMonthAboveAllowedRange() {
      // given
      PaymentRequest request = givenPaymentRequest(13, YearMonth.now().getYear());

      // when
      boolean result = validator.isValid(request, context);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptFutureExpiryDate() {
      // given
      YearMonth future = YearMonth.now().plusYears(30);
      PaymentRequest request = givenPaymentRequest(future);

      // when
      boolean result = validator.isValid(request, null);

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptCurrentExpiryMonth() {
      // given
      YearMonth now = YearMonth.now();
      PaymentRequest request = givenPaymentRequest(now);

      // when
      boolean result = validator.isValid(request, null);

      // then
      assertThat(result).isTrue();
    }
  }

  @Nested
  class ValidationScenarios {

    @Test
    void shouldRejectRequestWithExpiryYearInThePast() {
      // given
      YearMonth pastYear = YearMonth.now().minusYears(1);
      PaymentRequest request = givenPaymentRequest(pastYear);
      givenConstraintViolation(YEAR_IN_THE_PAST_MESSAGE, EXPIRY_YEAR_PROPERTY);

      // when
      boolean result = validator.isValid(request, context);

      // then
      assertThat(result).isFalse();
      thenConstraintViolationShouldBeAdded(YEAR_IN_THE_PAST_MESSAGE, EXPIRY_YEAR_PROPERTY);
    }

    @Test
    void shouldRejectExpiredCardAndAddViolationToExpiryMonth() {
      // given
      YearMonth pastMonth = YearMonth.now().minusMonths(1);
      PaymentRequest request = givenPaymentRequest(pastMonth);
      givenConstraintViolation(CARD_EXPIRED_MESSAGE, EXPIRY_MONTH_PROPERTY);

      // when
      boolean result = validator.isValid(request, context);

      // then
      assertThat(result).isFalse();
      thenConstraintViolationShouldBeAdded(CARD_EXPIRED_MESSAGE, EXPIRY_MONTH_PROPERTY);
    }
  }

  private void givenConstraintViolation(String message, String propertyName) {
    given(context.buildConstraintViolationWithTemplate(message))
        .willReturn(violationBuilder);
    given(violationBuilder.addPropertyNode(propertyName))
        .willReturn(nodeBuilder);
    given(nodeBuilder.addConstraintViolation())
        .willReturn(context);
  }

  private void thenConstraintViolationShouldBeAdded(String message, String propertyName) {
    then(context).should().disableDefaultConstraintViolation();
    then(context).should().buildConstraintViolationWithTemplate(message);
    then(violationBuilder).should().addPropertyNode(propertyName);
    then(nodeBuilder).should().addConstraintViolation();
  }

  private PaymentRequest givenPaymentRequest(YearMonth expiryDate) {
    return givenPaymentRequest(
        expiryDate.getMonthValue(),
        expiryDate.getYear()
    );
  }

  private PaymentRequest givenPaymentRequest(Integer expiryMonth, Integer expiryYear) {
    return PaymentRequest.builder()
        .expiryMonth(expiryMonth)
        .expiryYear(expiryYear)
        .build();
  }
}