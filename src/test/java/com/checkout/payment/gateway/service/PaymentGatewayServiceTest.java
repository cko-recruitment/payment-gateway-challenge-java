package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.acquirer.AcquirerClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.AcquirerException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.observability.PaymentMetrics;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentGatewayServiceTest {

  private final AcquirerClient acquirer = mock(AcquirerClient.class);
  private final PaymentsRepository repository = mock(PaymentsRepository.class);
  private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  private final PaymentGatewayService service =
      new PaymentGatewayService(repository, acquirer, new PaymentMetrics(metrics));
  private final PostPaymentRequest request =
      new PostPaymentRequest("12345678900001", 12, 2099, "GBP", 10, "123");

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void storesMaskedPaymentForAcquirerOutcome(boolean authorized) {
    when(acquirer.authorize(request)).thenReturn(authorized);

    PostPaymentResponse result = service.processPayment(request);

    assertNotNull(result.id());
    assertEquals(new PostPaymentResponse(result.id(),
        authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        "0001", 12, 2099, "GBP", 10), result);
    verify(acquirer).authorize(request);
    verify(repository).add(result);
    assertSingleOutcome(authorized ? "authorized" : "declined");
  }

  @Test
  void retrievesExistingPayment() {
    UUID id = UUID.randomUUID();
    PostPaymentResponse payment =
        new PostPaymentResponse(id, PaymentStatus.AUTHORIZED, "0001", 12, 2099, "GBP", 10);
    when(repository.get(id)).thenReturn(Optional.of(payment));

    assertEquals(payment, service.getPaymentById(id));
    verifyNoInteractions(acquirer);
    assertEquals(0, metrics.getMeters().size());
  }

  @Test
  void missingPaymentRaisesNotFound() {
    UUID id = UUID.randomUUID();
    when(repository.get(id)).thenReturn(Optional.empty());

    assertThrows(EventProcessingException.class, () -> service.getPaymentById(id));
    verifyNoInteractions(acquirer);
    assertEquals(0, metrics.getMeters().size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void acquirerFailureDoesNotStorePayment(boolean unavailable) {
    when(acquirer.authorize(request)).thenThrow(new AcquirerException(unavailable));

    assertThrows(AcquirerException.class, () -> service.processPayment(request));

    verify(acquirer).authorize(request);
    verifyNoInteractions(repository);
    assertSingleOutcome(unavailable ? "acquirer_unavailable" : "acquirer_error");
  }

  private void assertSingleOutcome(String outcome) {
    assertEquals(1, metrics.getMeters().size());
    var counter = metrics.get("payments.processed").tag("outcome", outcome).counter();
    assertEquals(1.0, counter.count());
    assertEquals(1, counter.getId().getTags().size());
  }
}
