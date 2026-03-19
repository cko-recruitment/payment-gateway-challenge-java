package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.util.CardUtils;
import com.fasterxml.jackson.annotation.JsonProperty;

public record BankPaymentRequest(
        @JsonProperty("card_number") String cardNumber,
        @JsonProperty("expiry_date") String expiryDate,
        @JsonProperty("currency") PaymentCurrency currency,
        @JsonProperty("amount") long amount,
        @JsonProperty("cvv") String cvv
) {
    @Override
    public String toString() {
        return "BankPaymentRequest{" +
                "cardNumber='" +  CardUtils.getLastFourCardDigits(cardNumber) +
                ", expiryDate='" + expiryDate +
                ", currency='" + currency +
                ", amount=" + amount +
                ", cvv=***" +
                '}';
    }
}
