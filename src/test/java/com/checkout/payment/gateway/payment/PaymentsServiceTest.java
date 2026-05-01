package com.checkout.payment.gateway.payment;

import static com.checkout.payment.gateway.TestUtils.givenMounteBankResponse;
import static com.checkout.payment.gateway.TestUtils.givenPayment;
import static com.checkout.payment.gateway.TestUtils.givenPaymentResponse;
import static com.checkout.payment.gateway.TestUtils.givenValidPaymentRequest;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.checkout.payment.gateway.client.MounteBankClient;
import com.checkout.payment.gateway.client.dto.MounteBankResponse;
import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.payment.entity.PaymentStatus;
import com.checkout.payment.gateway.payment.entity.PaymentsRepository;
import com.checkout.payment.gateway.shared.exception.MounteBankException;
import com.checkout.payment.gateway.shared.exception.PaymentNotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentsServiceTest {

  private PaymentsService testObj;

  @Mock
  private PaymentsRepository paymentRepository;

  @Mock
  private PaymentMapper paymentMapper;

  @Mock
  private MounteBankClient mounteBankClient;

  @Captor
  private ArgumentCaptor<Payment> captorPayment;

  @BeforeEach
  void setup() {
    this.testObj = new PaymentsService(paymentRepository, paymentMapper, mounteBankClient);
  }

  @Nested
  class RetrievePaymentScenarios {

    @Test
    void whenPaymentExistsShouldReturn() {
      // given
      UUID paymentId = UUID.randomUUID();
      Payment payment = givenPayment();
      given(paymentRepository.findById(paymentId)).willReturn(of(payment));
      given(paymentMapper.toResponse(payment)).willReturn(givenPaymentResponse());

      // when
      PaymentResponse result = testObj.findById(paymentId);

      // then
      PaymentResponse expectedResponse = givenPaymentResponse();
      then(paymentMapper).should().toResponse(captorPayment.capture());
      assertThat(captorPayment.getValue())
          .usingRecursiveAssertion()
          .isEqualTo(payment);
      assertThat(result).usingRecursiveComparison()
          .ignoringFields("id")
          .isEqualTo(expectedResponse);
      assertThat(result.getId()).isNotNull();
    }

    @Test
    void whenPaymentNotExistsShouldThrowException() {
      // given
      UUID paymentId = UUID.randomUUID();
      given(paymentRepository.findById(paymentId)).willReturn(empty());

      // when then
      PaymentNotFoundException exception = assertThrows(
          PaymentNotFoundException.class, () -> testObj.findById(paymentId));
      assertThat(exception.getMessage()).contains("Payment not found for id: " + paymentId);
    }

  }

  @Nested
  class ProcessPaymentScenarios {

    @Test
    void whenProcessPaymentRetrieveShouldReturnAuthorized() {
      // given
      PaymentRequest request = givenValidPaymentRequest();

      Payment payment = givenPayment().toBuilder()
          .id(null)
          .authorizationCode(null)
          .status(null)
          .createdAt(null)
          .build();

      given(paymentMapper.toEntity(request)).willReturn(payment);
      MounteBankResponse clientResponse = givenMounteBankResponse(TRUE);
      given(mounteBankClient.executePayment(payment)).willReturn(clientResponse);

      Payment persistedPayment = givenPayment().toBuilder()
          .id(UUID.randomUUID())
          .authorizationCode(clientResponse.authorizationCode())
          .status(PaymentStatus.AUTHORIZED)
          .createdAt(LocalDateTime.now())
          .build();

      given(paymentRepository.save(any(Payment.class))).willReturn(persistedPayment);
      given(paymentMapper.toResponse(any(Payment.class))).willReturn(givenPaymentResponse());

      // when
      PaymentResponse result = testObj.processPayment(request);

      // then
      PaymentResponse expectedResponse = givenPaymentResponse();
      assertThat(result).usingRecursiveComparison()
          .ignoringFields("id")
          .isEqualTo(expectedResponse);
      assertThat(result.getId()).isNotNull();

      then(paymentMapper).should().toEntity(request);
      then(mounteBankClient).should().executePayment(payment);

      then(paymentRepository).should().save(captorPayment.capture());
      Payment savedPayment = captorPayment.getValue();
      assertThat(savedPayment).usingRecursiveComparison()
          .ignoringFields("id", "cardNumber", "authorizationCode", "status", "createdAt")
          .isEqualTo(payment);
      assertThat(savedPayment.id()).isNotNull();
      assertThat(savedPayment.createdAt()).isNotNull();
      assertThat(savedPayment.authorizationCode()).isEqualTo(clientResponse.authorizationCode());
      assertThat(savedPayment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
      assertThat(savedPayment.cardNumber()).isEqualTo("8990");
      then(paymentMapper).should().toResponse(persistedPayment);
    }

    @Test
    void whenProcessPaymentRetrieveShouldReturnDeclined() {
      // given
      PaymentRequest request = givenValidPaymentRequest();

      Payment payment = givenPayment().toBuilder()
          .id(null)
          .authorizationCode(null)
          .status(null)
          .createdAt(null)
          .build();

      given(paymentMapper.toEntity(request)).willReturn(payment);
      MounteBankResponse clientResponse = givenMounteBankResponse(FALSE);
      given(mounteBankClient.executePayment(payment)).willReturn(clientResponse);

      Payment persistedPayment = givenPayment().toBuilder()
          .id(UUID.randomUUID())
          .authorizationCode(clientResponse.authorizationCode())
          .status(PaymentStatus.DECLINED)
          .createdAt(LocalDateTime.now())
          .build();

      given(paymentRepository.save(any(Payment.class))).willReturn(persistedPayment);
      given(paymentMapper.toResponse(any(Payment.class))).willReturn(givenPaymentResponse());

      // when
      PaymentResponse result = testObj.processPayment(request);

      // then
      PaymentResponse expectedResponse = givenPaymentResponse();
      assertThat(result).usingRecursiveComparison()
          .ignoringFields("id")
          .isEqualTo(expectedResponse);
      assertThat(result.getId()).isNotNull();

      then(paymentMapper).should().toEntity(request);
      then(mounteBankClient).should().executePayment(payment);

      then(paymentRepository).should().save(captorPayment.capture());
      Payment savedPayment = captorPayment.getValue();
      assertThat(savedPayment)
          .usingRecursiveComparison()
          .ignoringFields("id", "cardNumber", "authorizationCode", "status", "createdAt")
          .isEqualTo(payment);
      assertThat(savedPayment.id()).isNotNull();
      assertThat(savedPayment.createdAt()).isNotNull();
      assertThat(savedPayment.authorizationCode()).isEqualTo(clientResponse.authorizationCode());
      assertThat(savedPayment.cardNumber()).isEqualTo("8990");
      assertThat(savedPayment.status()).isEqualTo(PaymentStatus.DECLINED);
      then(paymentMapper).should().toResponse(persistedPayment);
    }

    @Test
    void whenProcessPaymentRetrieveNotProcessDueToClientError() {
      // given
      PaymentRequest request = givenValidPaymentRequest();

      Payment payment = givenPayment().toBuilder()
          .id(null)
          .authorizationCode(null)
          .status(null)
          .createdAt(null)
          .build();

      given(paymentMapper.toEntity(request)).willReturn(payment);
      given(mounteBankClient.executePayment(payment)).willThrow(
          new MounteBankException(new Exception("Client error")));

      // when
      assertThrows(MounteBankException.class, () -> testObj.processPayment(request));

      // then
      then(paymentMapper).should().toEntity(request);
      then(mounteBankClient).should().executePayment(payment);
      then(paymentRepository).should(never()).save(any());
      then(paymentMapper).should(never()).toResponse(any());
    }

  }

}