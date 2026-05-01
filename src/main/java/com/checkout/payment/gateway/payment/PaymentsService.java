package com.checkout.payment.gateway.payment;


import static com.checkout.payment.gateway.payment.entity.PaymentStatus.AUTHORIZED;
import static com.checkout.payment.gateway.payment.entity.PaymentStatus.DECLINED;
import static java.time.LocalDateTime.now;

import com.checkout.payment.gateway.client.MounteBankClient;
import com.checkout.payment.gateway.client.dto.MounteBankResponse;
import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.payment.entity.PaymentStatus;
import com.checkout.payment.gateway.payment.entity.PaymentsRepository;
import com.checkout.payment.gateway.shared.exception.PaymentNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
class PaymentsService {

  private final PaymentsRepository paymentsRepository;

  private final PaymentMapper paymentMapper;

  private final MounteBankClient mounteBankClient;

  PaymentResponse findById(UUID paymentId) {
    return paymentsRepository.findById(paymentId)
        .map(paymentMapper::toResponse)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }

  PaymentResponse processPayment(PaymentRequest request) {
    Payment payment = paymentMapper.toEntity(request);
    MounteBankResponse mounteBankResponse = mounteBankClient.executePayment(payment);
    Payment persistedPayment = paymentsRepository.save(
        buildEntityToPersist(payment, mounteBankResponse));
    return paymentMapper.toResponse(persistedPayment);
  }

  private Payment buildEntityToPersist(Payment payment, MounteBankResponse mounteBankResponse) {
    return payment.toBuilder()
        .cardNumber(payment.getCardNumberLastFour())
        .status(manageStatus(mounteBankResponse.authorized()))
        .authorizationCode(mounteBankResponse.authorizationCode())
        .id(UUID.randomUUID())
        .createdAt(now())
        .build();
  }

  private PaymentStatus manageStatus(Boolean authorized) {
    return authorized ? AUTHORIZED : DECLINED;
  }
}
