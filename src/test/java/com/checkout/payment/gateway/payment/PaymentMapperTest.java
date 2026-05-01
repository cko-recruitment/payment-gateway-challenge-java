package com.checkout.payment.gateway.payment;


import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.generated.api.model.CurrencyEnum;
import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.generated.api.model.PaymentStatusEnum;
import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.payment.entity.PaymentStatus;
import com.checkout.payment.gateway.shared.enums.Currency;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMapperTest {

  private final PaymentMapper mapper = new PaymentMapperImpl();

  @Test
  void whenToEntityWithNullRequest() {
    // given when then
    assertThat(mapper.toEntity(null)).isNull();
  }

  @Test
  void whenToEntity() {
    // given
    PaymentRequest request = PaymentRequest.builder()
        .cardNumber("4242424242424242")
        .expiryMonth(12)
        .expiryYear(2024)
        .cvv("123")
        .currency(CurrencyEnum.USD)
        .amount(10)
        .build();

    // when
    Payment entity = mapper.toEntity(request);

    // then
    Payment expectedEntity = Payment.builder()
        .cardNumber("4242424242424242")
        .expiryMonth(12)
        .expiryYear(2024)
        .cvv("123")
        .currency(Currency.USD)
        .amount(10)
        .build();

    assertThat(entity).usingRecursiveAssertion().isEqualTo(expectedEntity);
  }

  @Test
  void whenToResponse() {
    // given
    UUID paymentId = UUID.randomUUID();

    Payment entity = Payment.builder()
        .cardNumber("4242424242424242")
        .expiryMonth(12)
        .expiryYear(2024)
        .cvv("123")
        .currency(Currency.USD)
        .amount(10)
        .createdAt(LocalDateTime.now())
        .id(paymentId)
        .status(PaymentStatus.DECLINED)
        .build();

    // when
    PaymentResponse response = mapper.toResponse(entity);

    // then
    PaymentResponse expectedResponse = PaymentResponse.builder()
        .cardNumberLastFour("4242")
        .expiryMonth(12)
        .expiryYear(2024)
        .currency(CurrencyEnum.USD)
        .amount(10)
        .id(paymentId)
        .status(PaymentStatusEnum.DECLINED)
        .build();

    assertThat(response).usingRecursiveAssertion().isEqualTo(expectedResponse);
  }

  @Test
  void whenToResponseNullEntity() {
    // given when then
    assertThat(mapper.toEntity(null)).isNull();
  }
}