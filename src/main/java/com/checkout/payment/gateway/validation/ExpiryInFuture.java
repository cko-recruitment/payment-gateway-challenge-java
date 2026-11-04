package com.checkout.payment.gateway.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint asserting that a card's expiry month and year together are not in the
 * past. A card is valid until its expiry month has passed.
 */
@Documented
@Constraint(validatedBy = ExpiryInFutureValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpiryInFuture {

  String message() default "card has expired: the expiry month and year are in the past";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
