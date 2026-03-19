package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class PaymentGatewayService {

  private final PaymentRepository paymentsRepository;
  private final BankSimulatorClient bankSimulatorClient;

  public PaymentGatewayService(PaymentRepository paymentsRepository, 
                               BankSimulatorClient bankSimulatorClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankSimulatorClient = bankSimulatorClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    log.debug("Requesting internal payment retrieval for ID: {}", id);
    return paymentsRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Payment record not found for ID: {}", id);
                return new EventProcessingException("Invalid ID");
            });
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest,
      UUID paymentId, UUID merchantId) {
    
    String cardNumberLastFour = paymentRequest.cardNumber().length() >= 4
            ? paymentRequest.cardNumber().substring(paymentRequest.cardNumber().length() - 4) 
            : paymentRequest.cardNumber();

    PostPaymentResponse processingState = new PostPaymentResponse(
            paymentId,
            merchantId,
            PaymentStatus.PROCESSING,
            cardNumberLastFour,
            paymentRequest.expiryMonth(),
            paymentRequest.expiryYear(),
            paymentRequest.currency(),
            paymentRequest.amount()
    );

    // Distributed Idempotency control via Persistence layer simulation
    Optional<PostPaymentResponse> existingPayment = paymentsRepository.saveIfAbsent(processingState);
    if (existingPayment.isPresent()) {
        PostPaymentResponse cached = existingPayment.get();
        if (cached.status() == PaymentStatus.PROCESSING) {
            log.warn("Concurrent processing attempt detected for paymentId: {}. Rejecting as Conflict.", paymentId);
            throw new EventProcessingException("Payment is currently being processed");
        }
        log.info("Idempotency hit for paymentId: {}. Returning identical cached response.", paymentId);
        return cached;
    }

    log.info("Starting payment authorization for merchant: {}. Amount: {} {}", merchantId, paymentRequest.amount(), paymentRequest.currency());

    BankPaymentRequest bankRequest = new BankPaymentRequest(
            paymentRequest.cardNumber(),
            paymentRequest.expiryMonth() + "/" + paymentRequest.expiryYear(),
            paymentRequest.currency(),
            paymentRequest.amount(),
            paymentRequest.cvv()
    );

    PaymentStatus finalStatus;
    try {
        BankPaymentResponse bankResponse = bankSimulatorClient.processPayment(bankRequest);
        finalStatus = bankResponse != null && bankResponse.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
        log.info("Payment authorization final status for merchant {}: {}", merchantId, finalStatus);
    } catch (WebClientResponseException e) {
        log.warn("Payment authorization rejected by upstream bank client: {} for merchant {}", e.getStatusCode(), merchantId);
        finalStatus = PaymentStatus.REJECTED;
    } catch (Exception e) {
        log.error("Unexpected failure during payment authorization for merchant {}: {}", merchantId, e.getMessage());
        finalStatus = PaymentStatus.REJECTED;
    }

    PostPaymentResponse finalResponse = new PostPaymentResponse(
            paymentId,
            merchantId,
            finalStatus,
            cardNumberLastFour,
            paymentRequest.expiryMonth(),
            paymentRequest.expiryYear(),
            paymentRequest.currency(),
            paymentRequest.amount()
    );

    // Update the repository with the final status
    paymentsRepository.save(finalResponse);

    return finalResponse;
  }
}
