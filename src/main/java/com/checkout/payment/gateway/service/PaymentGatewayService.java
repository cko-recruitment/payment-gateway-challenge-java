package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.acquirer.AcquirerClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.AcquirerException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.observability.PaymentMetrics;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private final PaymentsRepository paymentsRepository;
  private final AcquirerClient acquirer;
  private final PaymentMetrics metrics;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, AcquirerClient acquirer,
      PaymentMetrics metrics) {
    this.paymentsRepository = paymentsRepository;
    this.acquirer = acquirer;
    this.metrics = metrics;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Looking up payment {}", id);
    return paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException("Payment not found"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request) {
    UUID id = UUID.randomUUID();
    boolean authorized;
    try {
      authorized = acquirer.authorize(request);
    } catch (AcquirerException ex) {
      metrics.recordAcquirerFailure(ex.isUnavailable());
      LOG.warn("Payment {} acquirer failure: {}", id, ex.getMessage());
      throw ex;
    }
    PostPaymentResponse payment = new PostPaymentResponse(id,
        authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        request.cardNumberLastFour(), request.expiryMonth(), request.expiryYear(),
        request.currency(), request.amount());
    paymentsRepository.add(payment);
    metrics.recordPayment(authorized);
    LOG.info("Payment {} outcome {}", id, payment.status());
    return payment;
  }
}
