package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Idempotency tests for {@link PaymentGatewayService}.
 *
 * <p>Verifies that duplicate and concurrent requests with the same idempotency key
 * result in exactly one bank call and a consistent response.</p>
 */
class PaymentGatewayIdempotencyTest {

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
   * Verifies that a duplicate request with the same idempotency key returns the stored repository
   * response without calling the bank again.
   */
  @Test
  void whenSameIdempotencyKeyIsUsedThenCachedResponseIsReturned() {
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode(UUID.randomUUID());
    when(responseSpec.bodyToMono(BankPaymentResponse.class)).thenReturn(Mono.just(bankResponse));

    UUID dupKey = UUID.randomUUID();
    PostPaymentResponse first = service.processPayment(buildRequest("2222405343248877"), dupKey.toString()).block();

    when(paymentsRepository.get(dupKey)).thenReturn(Optional.of(first));

    PostPaymentResponse second = service.processPayment(buildRequest("2222405343248877"), dupKey.toString()).block();

    assertThat(second).isEqualTo(first);
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
    verify(paymentsRepository, times(2)).get(dupKey);
  }

  /**
   * Verifies that when two requests with the same idempotency key arrive simultaneously,
   * only one bank call is made and both callers receive the same response.
   */
  @Test
  void whenTwoConcurrentRequestsWithSameKeyArriveThenBankIsCalledOnlyOnce() throws Exception {
    Sinks.One<BankPaymentResponse> sink = Sinks.one();
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(sink.asMono());

    String sharedKey = UUID.randomUUID().toString();
    PostPaymentRequest request = buildRequest("2222405343248877");

    CountDownLatch bothStarted = new CountDownLatch(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<PostPaymentResponse> future1 = executor.submit(() -> {
      bothStarted.countDown();
      bothStarted.await();
      return service.processPayment(request, sharedKey).block();
    });
    Future<PostPaymentResponse> future2 = executor.submit(() -> {
      bothStarted.countDown();
      bothStarted.await();
      return service.processPayment(request, sharedKey).block();
    });

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode(UUID.randomUUID());
    sink.tryEmitValue(bankResponse);

    PostPaymentResponse r1 = future1.get();
    PostPaymentResponse r2 = future2.get();
    executor.shutdown();

    assertThat(r1).isNotNull();
    assertThat(r2).isNotNull();
    assertThat(r1.getId()).isEqualTo(r2.getId());
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
  }

  /**
   * Verifies that a second request with the same idempotency key that arrives while the first
   * is still in-flight receives the same in-flight Mono and does not trigger a second bank call.
   */
  @Test
  void whenSecondRequestArrivesDuringFirstInFlightThenSameMonoIsReused() throws Exception {
    Sinks.One<BankPaymentResponse> sink = Sinks.one();
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(sink.asMono());

    String sharedKey = UUID.randomUUID().toString();
    PostPaymentRequest request = buildRequest("2222405343248877");

    Mono<PostPaymentResponse> firstMono = service.processPayment(request, sharedKey);
    Mono<PostPaymentResponse> secondMono = service.processPayment(request, sharedKey);

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode(UUID.randomUUID());
    sink.tryEmitValue(bankResponse);

    PostPaymentResponse r1 = firstMono.block();
    PostPaymentResponse r2 = secondMono.block();

    assertThat(r1).isNotNull();
    assertThat(r2).isNotNull();
    assertThat(r1.getId()).isEqualTo(r2.getId());
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
    verify(webClient, times(1)).post();
  }

  /**
   * Verifies that when the bank is unreachable, the in-flight cache entry is cleaned up so that
   * a subsequent retry with the same idempotency key is able to attempt the bank call again.
   */
  @Test
  void whenBankIsUnreachableThenInFlightCacheIsCleanedUpSoRetryCanProceed() {
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(Mono.error(new java.net.ConnectException("Connection refused")))
        .thenReturn(Mono.just(authorizedBankResponse()));

    String key = UUID.randomUUID().toString();
    PostPaymentRequest request = buildRequest("2222405343248877");

    assertThatThrownBy(() -> service.processPayment(request, key).block())
        .isInstanceOf(EventProcessingException.class)
        .hasMessage("Bank unreachable");

    PostPaymentResponse retryResult = service.processPayment(request, key).block();

    assertThat(retryResult).isNotNull();
    assertThat(retryResult.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
  }

  /**
   * Verifies that two requests with identical payment details (card, amount, currency, expiry)
   * but different idempotency keys both reach the bank independently.
   * This documents the current behaviour and highlights that fingerprint-level deduplication
   * is not yet implemented — both calls succeed and produce separate payment records.
   */
  @Test
  void whenSamePaymentDetailsUsedWithDifferentKeysThenBothCallsReachBank() {
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(Mono.just(authorizedBankResponse()))
        .thenReturn(Mono.just(authorizedBankResponse()));

    PostPaymentRequest request = buildRequest("2222405343248877");
    String key1 = UUID.randomUUID().toString();
    String key2 = UUID.randomUUID().toString();

    PostPaymentResponse r1 = service.processPayment(request, key1).block();
    PostPaymentResponse r2 = service.processPayment(request, key2).block();

    assertThat(r1).isNotNull();
    assertThat(r2).isNotNull();
    assertThat(r1.getId()).isNotEqualTo(r2.getId());
    verify(paymentsRepository, times(2)).add(any(PostPaymentResponse.class));
    verify(webClient, times(2)).post();
  }

  /**
   * Verifies that two requests with the same idempotency key but different payment details
   * (e.g. different amounts) return the original stored result without re-calling the bank.
   * The second request's differing details are ignored once the key is resolved.
   */
  @Test
  void whenSameKeyUsedWithDifferentPaymentDetailsThenOriginalResponseIsReturned() {
    when(responseSpec.bodyToMono(BankPaymentResponse.class))
        .thenReturn(Mono.just(authorizedBankResponse()));

    UUID sharedKey = UUID.randomUUID();
    PostPaymentResponse first = service.processPayment(buildRequest("2222405343248877"), sharedKey.toString()).block();

    when(paymentsRepository.get(sharedKey)).thenReturn(Optional.of(first));

    PostPaymentRequest differentRequest = buildRequest("2222405343248877");
    differentRequest.setAmount(99999);
    PostPaymentResponse second = service.processPayment(differentRequest, sharedKey.toString()).block();

    assertThat(second).isEqualTo(first);
    assertThat(second.getAmount()).isEqualTo(first.getAmount());
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
    verify(webClient, times(1)).post();
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

  private BankPaymentResponse authorizedBankResponse() {
    BankPaymentResponse r = new BankPaymentResponse();
    r.setAuthorized(true);
    r.setAuthorizationCode(UUID.randomUUID());
    return r;
  }
}
