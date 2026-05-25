package com.checkout.payment.gateway.model.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ExpiryDateValidator.class)
@Target({ ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidExpiryDate {
  String message() default "Card is expired";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
