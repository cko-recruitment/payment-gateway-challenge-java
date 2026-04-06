package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link PaymentGatewayService}.
 *
 * <p>Verifies payment processing logic using a mocked {@link WebClient} and
 * {@link PaymentsRepository}, covering authorized, declined, and rejected outcomes
 * as well as retrieval of previously processed payments.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentGatewayServiceTest {

  private PaymentsRepository paymentsRepository;
  private WebClient webClient;
  private WebClient.RequestBodyUriSpec requestBodyUriSpec;
  private WebClient.RequestBodySpec requestBodySpec;
  private WebClient.RequestHeadersSpec requestHeadersSpec;
  private WebClient.ResponseSpec responseSpec;
  private PaymentGatewayService service;

  /**
   * Sets up mocked WebClient chain and service instance before each test.
   */
  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    paymentsRepository = mock(PaymentsRepository.class);
    webClient = mock(WebClient.class);
    requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
    requestBodySpec = mock(WebClient.RequestBodySpec.class);
    requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
    responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

    service = new PaymentGatewayService(paymentsRepository, webClient);
  }

  /**
   * Verifies that when the bank returns {@code authorized: true},
   * the response has AUTHORIZED status, the correct authorization code as ID,
   * and the masked card number and last four digits are correctly derived from the card number.
   * The payment must also be stored in the repository.
   */
  @Test
  @Order(1)
  void whenBankAuthorizesPaymentThenAuthorizedResponseIsReturned() {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode(UUID.randomUUID());
    when(responseSpec.bodyToMono(BankPaymentResponse.class)).thenReturn(Mono.just(bankResponse));

    UUID idempotencyKey1 = UUID.randomUUID();
    PostPaymentResponse result = service.processPayment(buildRequest("2222405343248877"), idempotencyKey1.toString()).block();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(result.getId()).isNotNull();
    assertThat(result.getMaskedCardNumber()).isEqualTo("************8877");
    assertThat(result.getCardNumberLastFour()).isEqualTo(8877);
    verify(paymentsRepository).add(result);
  }

  /**
   * Verifies that when the bank returns {@code authorized: false},
   * the response has DECLINED status and the payment is stored in the repository.
   */
  @Test
  @Order(2)
  void whenBankDeclinesPaymentThenDeclinedResponseIsReturned() {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode(UUID.randomUUID());
    when(responseSpec.bodyToMono(BankPaymentResponse.class)).thenReturn(Mono.just(bankResponse));

    UUID idempotencyKey2 = UUID.randomUUID();
    PostPaymentResponse result = service.processPayment(buildRequest("2222405343248878"), idempotencyKey2.toString()).block();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verify(paymentsRepository).add(result);
  }

  /**
   * Verifies that when the bank returns a 503 Service Unavailable (card number ending in 0),
   * the payment is stored with REJECTED status and returned to the caller rather than propagating an error.
   */
  @Test
  @Order(3)
  void whenBankReturns503ThenRejectedResponseIsStoredAndReturned() {
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(Mono.error(WebClientResponseException.create(503, "Service Unavailable", null, null, null)));

    UUID idempotencyKey3 = UUID.randomUUID();
    PostPaymentResponse result = service.processPayment(buildRequest("2222405343248870"), idempotencyKey3.toString()).block();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(result.getMaskedCardNumber()).isEqualTo("************8870");
    verify(paymentsRepository).add(result);
  }

  /**
   * Verifies that when the bank cannot be reached (e.g. connection refused),
   * an {@link EventProcessingException} with 503 SERVICE_UNAVAILABLE is propagated.
   */
  @Test
  @Order(4)
  void whenBankIsUnreachableThenServiceUnavailableExceptionIsThrown() {
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(Mono.error(new java.net.ConnectException("Connection refused")));

    assertThatThrownBy(() -> service.processPayment(buildRequest("2222405343248877"), UUID.randomUUID().toString()).block())
        .isInstanceOf(EventProcessingException.class)
        .hasMessage("Bank unreachable");
  }

  /**
   * Verifies that when a null idempotency key is supplied,
   * an {@link EventProcessingException} with 400 BAD_REQUEST is thrown.
   */
  @Test
  @Order(5)
  void whenNullIdempotencyKeyThenEventProcessingExceptionIsThrown() {
    assertThatThrownBy(() -> service.processPayment(buildRequest("2222405343248877"), null).block())
        .isInstanceOf(EventProcessingException.class)
        .hasMessage("Idempotency-Key header is required");
  }

  /**
   * Verifies that when an invalid idempotency key is supplied,
   * an {@link com.checkout.payment.gateway.exception.EventProcessingException} is thrown
   * with an appropriate message.
   */
  @Test
  @Order(6)
  void whenInvalidIdempotencyKeyThenEventProcessingExceptionIsThrown() {
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(Mono.error(new java.net.ConnectException("Connection refused")));

    assertThatThrownBy(() -> service.processPayment(buildRequest("2222405343248877"), "not-a-uuid").block())
        .isInstanceOf(EventProcessingException.class)
        .hasMessage("Idempotency-Key must be a valid UUID");
  }

  /**
   * Verifies that retrieving a payment by a known ID returns the stored payment response.
   */
  @Test
  @Order(6)
  void whenPaymentExistsThenGetPaymentByIdReturnsIt() {
    UUID id = UUID.randomUUID();
    PostPaymentResponse stored = new PostPaymentResponse();
    stored.setId(id);
    stored.setStatus(PaymentStatus.AUTHORIZED);
    when(paymentsRepository.get(id)).thenReturn(Optional.of(stored));

    PostPaymentResponse result = service.getPaymentById(id);

    assertThat(result).isEqualTo(stored);
  }

  /**
   * Verifies that retrieving a payment by an unknown ID throws
   * {@link com.checkout.payment.gateway.exception.EventProcessingException}.
   */
  @Test
  @Order(6)
  void whenPaymentDoesNotExistThenGetPaymentByIdThrows() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(EventProcessingException.class)
        .hasMessage("Invalid ID");
  }

  /**
   * Verifies that the masked card number is computed correctly for cards of varying length
   * (14–19 digits), masking all digits except the last four.
   */
  @Test
  @Order(7)
  void maskedCardNumberHandlesDynamicCardLength() {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode(UUID.randomUUID());
    when(responseSpec.bodyToMono(BankPaymentResponse.class)).thenReturn(Mono.just(bankResponse));

    PostPaymentResponse result = service.processPayment(buildRequest("22224053432488771"), UUID.randomUUID().toString()).block();

    assertThat(result.getMaskedCardNumber()).isEqualTo("*************8771");
    assertThat(result.getCardNumberLastFour()).isEqualTo(8771);
  }

  /**
   * Builds a valid {@link PostPaymentRequest} with the given card number for use in tests.
   *
   * @param cardNumber the card number to set on the request
   * @return a fully populated request object
   */
  private PostPaymentRequest buildRequest(String cardNumber) {
    PostPaymentRequest req = new PostPaymentRequest();
    req.setCardNumber(cardNumber);
    req.setExpiryMonth(12);
    req.setExpiryYear(2099);
    req.setCurrency("USD");
    req.setAmount(100);
    req.setCvv(123);
    return req;
  }
}
