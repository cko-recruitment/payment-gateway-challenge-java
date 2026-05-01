package com.checkout.payment.gateway.idempotency;

import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

  private final IdempotencyRepository idempotencyRepository;

  public Optional<PaymentResponse> findBy(UUID idempotencyKey) {
    return idempotencyRepository.findBy(idempotencyKey);
  }

  public void save(UUID idempotencyKey, PaymentResponse paymentResponse) {
    idempotencyRepository.save(idempotencyKey, paymentResponse);
  }

}
