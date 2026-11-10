package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.ExpiringCard;
import com.checkout.payment.gateway.validation.ExpiryInFuture;
import com.checkout.payment.gateway.validation.SupportedCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

@ExpiryInFuture
@Schema(
    name = "PaymentRequest",
    description = """
        A card payment to process. Every field is required, and the whole request is validated
        before the gateway calls the bank: if any field is invalid the payment is never
        attempted.

        Beyond the per-field rules below, the expiry month and year together must not be in the
        past. A card is valid through the whole of its expiry month.""",
    example = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 4,
          "expiry_year": 2030,
          "currency": "GBP",
          "amount": 10500,
          "cvv": "123"
        }""")
public record PostPaymentRequest(
    @Schema(
        description = "The card number, digits only, with no spaces or dashes. Only the last "
            + "four digits are ever returned or stored.",
        example = "2222405343248877",
        minLength = 14,
        maxLength = 19,
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Size(min = 14, max = 19)
    @Pattern(regexp = DIGITS_ONLY_REGEX, message = "must contain digits only")
    String cardNumber,

    @Schema(
        description = "Month the card expires, 1-12. Not zero-padded.",
        example = "4",
        minimum = "1",
        maximum = "12",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(1)
    @Max(12)
    Integer expiryMonth,

    @Schema(
        description = "Year the card expires, as four digits.",
        example = "2030",
        minimum = "1000",
        maximum = "9999",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(value = 1000)
    @Max(value = 9999)
    Integer expiryYear,

    @Schema(
        description = "ISO 4217 currency code. The gateway accepts GBP, USD and EUR unless the "
            + "deployment overrides `PAYMENTS_SUPPORTED_CURRENCIES`.",
        example = "GBP",
        allowableValues = {"GBP", "USD", "EUR"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @SupportedCurrency
    String currency,

    @Schema(
        description = "Amount to charge, in the currency's **minor unit** and as a whole "
            + "number: `10500` is £105.00, not £10,500. Must be greater than zero.",
        example = "10500",
        minimum = "1",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Positive
    Integer amount,

    @Schema(
        description = "Card security code, 3 or 4 digits. Never returned or stored, and masked "
            + "in validation errors.",
        example = "123",
        minLength = 3,
        maxLength = 4,
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Size(min = 3, max = 4)
    @Pattern(regexp = DIGITS_ONLY_REGEX, message = "must contain digits only")
    String cvv
) implements Serializable, ExpiringCard {

  private static final String DIGITS_ONLY_REGEX = "[0-9]+";

  @Schema(hidden = true)
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }
}
