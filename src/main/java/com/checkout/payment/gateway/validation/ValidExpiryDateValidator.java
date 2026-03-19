package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.YearMonth;

public class ValidExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, PostPaymentRequest> {

    @Override
    public boolean isValid(PostPaymentRequest request, ConstraintValidatorContext context) {
        if (request == null) return true;
        
        try {
            YearMonth expiry = YearMonth.of(request.expiryYear(), request.expiryMonth());
            return expiry.isAfter(YearMonth.now()) || expiry.equals(YearMonth.now());
        } catch (Exception e) {
            // Invalid month/year combination will be caught by other validations or this one fails
            return false;
        }
    }
}
