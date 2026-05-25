package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.internal.BankResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("OptionalGetWithoutIsPresent")
class PaymentGatewayServiceTest {
  private BankClient bankClient;
  private PaymentsRepository repo;
  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    bankClient = mock(BankClient.class);
    repo = new PaymentsRepository();
    service = new PaymentGatewayService(repo, bankClient);
  }

  @Test
  void whenPaymentProcessedThenResponseIsStoredAndIdReturned() {
    PostPaymentRequest request = validRequest();

    var bankResponse = new BankResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("auth-123");
    when(bankClient.processPayment(request))
        .thenReturn(bankResponse);

    var expectedPaymentResponse = PaymentResponse.of(request, bankResponse);


    UUID paymentId = service.processPayment(request);
    Optional<PaymentResponse> actualResponse = repo.get(paymentId);


    verify(bankClient).processPayment(request);
    assertThat(actualResponse).isNotEmpty();
    assertThat(actualResponse.get())
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(expectedPaymentResponse);
  }

  @Test
  void whenBankDeclinesThenDeclinedPaymentIsStored() {
    PostPaymentRequest request = validRequest();

    BankResponse bankResponse = new BankResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("");
    when(bankClient.processPayment(request))
        .thenReturn(bankResponse);

    var expectedPaymentResponse = PaymentResponse.of(request, bankResponse);


    UUID paymentId = service.processPayment(request);
    Optional<PaymentResponse> actualResponse = repo.get(paymentId);


    verify(bankClient).processPayment(request);
    assertThat(actualResponse).isNotEmpty();
    assertThat(actualResponse.get())
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(expectedPaymentResponse);
  }

  @Test
  void whenPaymentExistsThenItIsReturned() {
    UUID id = UUID.randomUUID();

    var payment = new PaymentResponse();
    payment.setId(id);
    repo.add(payment);


    PaymentResponse actualResponse = service.getPaymentById(id);


    assertThat(actualResponse.getId()).isEqualTo(id);
  }

  @Test
  void whenPaymentDoesNotExistThenThrowsEventProcessing() {
    UUID id = UUID.randomUUID();


    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(EventProcessingException.class);
  }

  private PostPaymentRequest validRequest() {

    PostPaymentRequest request = new PostPaymentRequest();

    request.setCardNumber("4444333322221111");
    request.setExpiryMonth(12);
    request.setExpiryYear(2099);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");

    return request;
  }
}