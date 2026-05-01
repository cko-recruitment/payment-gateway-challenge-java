package com.checkout.payment.gateway.client;

import static com.checkout.payment.gateway.TestUtils.givenPayment;
import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.client.dto.MounteBankRequest;
import com.checkout.payment.gateway.payment.entity.Payment;
import org.junit.jupiter.api.Test;

class MounteBankMapperTest {

  private final MounteBankMapper testObject = new MounteBankMapperImpl();

  @Test
  void whenToRequestPaymentNullShouldReturnNull() {
    // given when then
    assertThat(testObject.toRequest(null)).isNull();
  }

  @Test
  void whenToRequestPaymentShouldReturn() {
    // given
    Payment payment = givenPayment()
        .toBuilder()
        .expiryMonth(11)
        .expiryYear(2028)
        .build();
    // when
    MounteBankRequest clientRequest = testObject.toRequest(payment);
    // then
    MounteBankRequest expectedRequest = MounteBankRequest.builder()
        .cardNumber(payment.cardNumber())
        .expiryDate("11/2028")
        .cvv(payment.cvv())
        .currency(payment.currency())
        .amount(payment.amount())
        .build();

    assertThat(clientRequest).usingRecursiveAssertion().isEqualTo(expectedRequest);
  }

  @Test
  void whenToRequestPaymenAndMonthStartWithZerotShouldReturn() {
    // given
    Payment payment = givenPayment().toBuilder()
        .expiryMonth(1)
        .expiryYear(2028)
        .build();

    // when
    MounteBankRequest clientRequest = testObject.toRequest(payment);
    // then
    MounteBankRequest expectedRequest = MounteBankRequest.builder()
        .cardNumber(payment.cardNumber())
        .expiryDate("01/2028")
        .cvv(payment.cvv())
        .currency(payment.currency())
        .amount(payment.amount())
        .build();

    assertThat(clientRequest).usingRecursiveAssertion().isEqualTo(expectedRequest);
  }
}