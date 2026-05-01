package com.checkout.payment.gateway.payment;


import static org.springframework.http.HttpStatus.CREATED;

import com.checkout.payment.gateway.generated.api.PaymentsApi;
import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.idempotency.IdempotencyService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
class PaymentsController implements PaymentsApi {

  private final PaymentsService paymentsService;
  private final IdempotencyService idempotencyService;

  @Override
  public ResponseEntity<PaymentResponse> getPaymentById(UUID id) {
    log.info("Retrieve payment paymentId={}", id);
    PaymentResponse response = paymentsService.findById(id);
    log.info("Finished retrieve payment paymentId={}", id);
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<PaymentResponse> processPayment(UUID idempotencyKey,
      PaymentRequest request) {
    log.info("Payment received to process idempotencyKey={}", idempotencyKey);
    return idempotencyService.findBy(idempotencyKey)
        .map(paymentResponse -> processIdempotency(idempotencyKey, paymentResponse))
        .orElseGet(() -> {
          log.info("Processing payment for idempotencyKey={}", idempotencyKey);
          PaymentResponse response = paymentsService.processPayment(request);
          idempotencyService.save(idempotencyKey, response);
          log.info("Payment processed successfully with idempotencyKey={}, paymentId={}",
              idempotencyKey, response.getId());
          return ResponseEntity.status(CREATED).body(response);
        });
  }

  private ResponseEntity<PaymentResponse> processIdempotency(UUID idempotencyKey,
      PaymentResponse paymentResponse) {
    log.info("Payment found for idempotencyKey={}, paymentId={}", idempotencyKey,
        paymentResponse.getId());
    return ResponseEntity.ok(paymentResponse);
  }
}
