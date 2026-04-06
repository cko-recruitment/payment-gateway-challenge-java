
// ------------------------------------------------------------------------------------------------
// ================================================================================================
// PACKAGE DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------

package com.checkout.payment.gateway.service;

// ------------------------------------------------------------------------------------------------
// ================================================================================================
// IMPORT DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

// ------------------------------------------------------------------------------------------------
// ================================================================================================
// CLASS DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------


/**
 * Service responsible for processing payments through the bank and retrieving stored payment results.
 *
 * <p>Idempotency is enforced in-memory via a {@link ConcurrentHashMap} of cached {@link Mono}s,
 * ensuring concurrent requests with the same key call the bank exactly once.</p>
 */
@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final WebClient webClient;

  // This MAP should be defined as cache
  // In flight requests are stored here to avoid duplicate inflight processing
  private final ConcurrentHashMap<UUID, Mono<PostPaymentResponse>> inFlightCache = new ConcurrentHashMap<>();

  /**
   * Constructs the service with its required dependencies.
   *
   * @param paymentsRepository repository for persisting and retrieving payment results
   * @param webClient          reactive HTTP client used to communicate with the bank
   */
  public PaymentGatewayService(PaymentsRepository paymentsRepository, WebClient webClient) {
    this.paymentsRepository = paymentsRepository;
    this.webClient = webClient;
  }

  /**
   * Retrieves a previously processed payment by its ID.
   *
   * @param id the UUID of the payment to retrieve
   * @return the stored {@link PostPaymentResponse}
   * @throws EventProcessingException with 404 NOT_FOUND if no payment exists for the given ID
   */
  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException("Invalid ID", HttpStatus.NOT_FOUND));
  }

  /**
   * Processes a payment request through the bank.
   *
   * <p>Uses the {@code idempotencyKey} to deduplicate concurrent or repeated requests.
   * The first request with a given key calls the bank and caches the result; all subsequent
   * requests with the same key receive the cached response without re-calling the bank.</p>
   *
   * @param paymentRequest the payment details to process
   * @param idempotencyKey a UUID string uniquely identifying this request
   * @return a {@link Mono} emitting the payment result
   * @throws EventProcessingException with 400 BAD_REQUEST if the key is not a valid UUID
   */
  public Mono<PostPaymentResponse> processPayment(PostPaymentRequest paymentRequest, String idempotencyKey) {
    if (idempotencyKey == null) {
      throw new EventProcessingException("Idempotency-Key header is required", HttpStatus.BAD_REQUEST);
    }
    UUID idempotencyKeyUuid;
    try {
      idempotencyKeyUuid = UUID.fromString(idempotencyKey);
    } catch (IllegalArgumentException e) {
      throw new EventProcessingException("Idempotency-Key must be a valid UUID",
          HttpStatus.BAD_REQUEST);
    }
    PostPaymentResponse existing = paymentsRepository.get(idempotencyKeyUuid).orElse(null);
    if (existing != null) {
      LOG.debug("Returning stored result for idempotency key {}", idempotencyKeyUuid);
      return Mono.just(existing);
    }
    return inFlightCache.computeIfAbsent(idempotencyKeyUuid,
        k -> buildPaymentMono(paymentRequest, k).cache());
  }

  /**
   * Builds the reactive pipeline that calls the bank and maps the result to a
   * {@link PostPaymentResponse}. Each branch constructs the response independently to avoid shared
   * mutable state across reactive subscriptions.
   *
   * @param paymentRequest     the payment details
   * @param idempotencyKeyUuid the idempotency key used as the payment ID
   * @return a cold {@link Mono} representing the bank call and result mapping
   */
  private Mono<PostPaymentResponse> buildPaymentMono(PostPaymentRequest paymentRequest,
      UUID idempotencyKeyUuid) {
    BankPaymentRequest bankRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        paymentRequest.getExpiryDate(),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount(),
        String.valueOf(paymentRequest.getCvv())
    );

    String maskedCardNumber = "*".repeat(paymentRequest.getCardNumber().length() - 4)
        + paymentRequest.getCardNumberLastFour();

    return webClient.post()
        .uri("http://localhost:8080/payments")
        .bodyValue(bankRequest)
        .retrieve()
        .bodyToMono(BankPaymentResponse.class)
        // map the bank response to a post payment response: authorized or declined
        .map(bankResponse -> {
          PostPaymentResponse response = this.buildResponse(idempotencyKeyUuid, paymentRequest,
              maskedCardNumber,
              bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
          paymentsRepository.add(response);
          LOG.info("Processed payment with ID {}, status={}", response.getId(),
              response.getStatus());
          return response;
        })
        // on error resume with a rejected response
        .onErrorResume(WebClientResponseException.class, ex -> {
          LOG.error("Bank returned HTTP {}: {}", ex.getStatusCode(), ex.getMessage());
          PostPaymentResponse rejected = this.buildResponse(idempotencyKeyUuid, paymentRequest,
              maskedCardNumber, PaymentStatus.REJECTED);
          paymentsRepository.add(rejected);
          LOG.info("Payment rejected due to bank error {}, ID={}", ex.getStatusCode(),
              rejected.getId());
          return Mono.just(rejected);
        })
        .onErrorResume(Exception.class, ex -> {
          LOG.error("Failed to reach bank: {}", ex.getMessage());
          return Mono.error(
              new EventProcessingException("Bank unreachable", HttpStatus.SERVICE_UNAVAILABLE));
        })
        .doFinally(e -> inFlightCache.remove(idempotencyKeyUuid));
  }


  /**
   * Builds a {@link PostPaymentResponse} from the given parameters.
   *
   * @param idempotencyKeyUuid idempotency UUID key
   * @param paymentRequest     Payment reqeust
   * @param status             Payment status
   * @return {@link PostPaymentResponse}
   */
  private PostPaymentResponse buildResponse(UUID idempotencyKeyUuid,
      PostPaymentRequest paymentRequest,
      String maskedCardNumber,
      PaymentStatus status) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setIdempotencyKey(idempotencyKeyUuid);
    response.setStatus(status);
    response.setCardNumberLastFour(paymentRequest.getCardNumberLastFour());
    response.setMaskedCardNumber(maskedCardNumber);
    response.setExpiryMonth(paymentRequest.getExpiryMonth());
    response.setExpiryYear(paymentRequest.getExpiryYear());
    response.setCurrency(paymentRequest.getCurrency());
    response.setAmount(paymentRequest.getAmount());
    return response;
  }

}
