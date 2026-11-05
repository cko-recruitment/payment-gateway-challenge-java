package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentData;
import com.checkout.payment.gateway.model.PaymentRequestData;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.processor.PaymentsProcessor;
import com.checkout.payment.gateway.repository.PaymentsPersistence;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsPersistence paymentsRepository;
  private final PaymentsProcessor paymentsProcessor;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      PaymentsProcessor paymentsProcessor) {
    this.paymentsRepository = paymentsRepository;
    this.paymentsProcessor = paymentsProcessor;
  }

  public Optional<PaymentData> getPaymentById(UUID id) {
    return paymentsRepository.get(id);
  }

  public UUID processPayment(PaymentRequestData paymentRequest) {
    BankPaymentResponse bankPaymentResponse = paymentsProcessor.process(
        paymentRequest);

    PaymentData paymentData = PaymentData.createPaymentData(bankPaymentResponse.isAuthorized(),
        paymentRequest.getCardNumberLastFour(),
        paymentRequest.expiryMonth(), paymentRequest.expiryYear(),
        paymentRequest.currency(), paymentRequest.amount(),
        bankPaymentResponse.authorizationCode());
    paymentsRepository.add(paymentData);

    return paymentData.id();
  }

}
