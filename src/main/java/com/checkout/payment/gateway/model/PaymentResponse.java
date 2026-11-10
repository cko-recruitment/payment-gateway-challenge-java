package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "PaymentResponse",
    description = """
        A payment the gateway has attempted, as the bank left it. Returned both when a payment
        is processed and when one is retrieved later.

        Card details are not echoed back: only the last four digits of the card number appear,
        and the CVV never does.""",
    example = """
        {
          "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "payment_status": "Authorized",
          "status": 200,
          "card_number_last_four": "8877",
          "expiry_month": 4,
          "expiry_year": 2030,
          "currency": "GBP",
          "amount": 10500
        }""")
public record PaymentResponse(
    @Schema(
        description = "Gateway ID for this payment. Use it with `GET /payment/{id}` to retrieve "
            + "the payment again.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,

    @Schema(
        description = "The bank's decision. `Authorized` means the funds were approved; "
            + "`Declined` means the bank refused them, which is still a successful request.",
        requiredMode = Schema.RequiredMode.REQUIRED)
    PaymentStatus paymentStatus,

    @Schema(
        description = "HTTP status of the response, repeated in the body for callers that log "
            + "the payload alone. Always `200` here.",
        example = "200",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int status,

    @Schema(
        description = "Last four digits of the card that was charged.",
        example = "8877",
        minLength = 4,
        maxLength = 4,
        requiredMode = Schema.RequiredMode.REQUIRED)
    String cardNumberLastFour,

    @Schema(
        description = "Month the card expires, 1-12, as submitted.",
        example = "4",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int expiryMonth,

    @Schema(
        description = "Year the card expires, as submitted.",
        example = "2030",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int expiryYear,

    @Schema(
        description = "ISO 4217 currency code the payment was taken in.",
        example = "GBP",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String currency,

    @Schema(
        description = "Amount charged, in the currency's minor unit: `10500` is £105.00.",
        example = "10500",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int amount,

    @Schema(
        description = "Why the payment was not taken, when the gateway has a reason to give. "
            + "Omitted from the response when absent.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true)
    @Nullable String rejectionMessage
) {

}
