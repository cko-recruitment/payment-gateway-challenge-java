package com.checkout.payment.gateway;

import static com.checkout.payment.gateway.payment.entity.PaymentStatus.AUTHORIZED;
import static com.checkout.payment.gateway.shared.enums.Currency.USD;

import com.checkout.payment.gateway.client.dto.MounteBankResponse;
import com.checkout.payment.gateway.generated.api.model.CurrencyEnum;
import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.generated.api.model.PaymentStatusEnum;
import com.checkout.payment.gateway.payment.entity.Payment;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtils {

  public static PaymentRequest givenValidPaymentRequest() {
    return PaymentRequest.builder()
        .cardNumber("2222405343248877")
        .expiryMonth(4)
        .expiryYear(2030)
        .currency(CurrencyEnum.GBP)
        .amount(100)
        .cvv("123")
        .build();
  }

  public static Payment givenPayment() {
    return givenPayment("12345678990");
  }

  public static Payment givenPayment(String cardNumber) {
    YearMonth future = YearMonth.now().plusYears(2);
    return Payment.builder()
        .id(UUID.randomUUID())
        .cardNumber(cardNumber)
        .expiryMonth(future.getMonth().getValue())
        .expiryYear(future.getYear())
        .cvv("123")
        .status(AUTHORIZED)
        .currency(USD)
        .amount(10)
        .createdAt(LocalDateTime.now())
        .build();
  }

  public static MounteBankResponse givenMounteBankResponse(Boolean authorized) {
    return MounteBankResponse.builder()
        .authorized(authorized)
        .authorizationCode(UUID.randomUUID())
        .build();
  }

  public static PaymentResponse givenPaymentResponse(UUID id, String cardNumber,
      PaymentStatusEnum status) {
    return PaymentResponse.builder()
        .id(id)
        .amount(10)
        .currency(CurrencyEnum.USD)
        .status(status)
        .expiryMonth(12)
        .expiryYear(2024)
        .cardNumberLastFour(cardNumber).build();
  }

  public static PaymentResponse givenPaymentResponse() {
    return givenPaymentResponse(UUID.randomUUID(), "4321", PaymentStatusEnum.AUTHORIZED);
  }
}
