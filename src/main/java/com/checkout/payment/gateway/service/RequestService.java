package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PaymentData;
import com.checkout.payment.gateway.model.PaymentRequestData;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RequestService {

  private static final Logger LOG = LoggerFactory.getLogger(RequestService.class);

  private final PaymentGatewayService paymentGatewayService;

  public RequestService(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentGatewayService.getPaymentById(id).map(RequestService::toPostPaymentResponse)
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public UUID processPayment(PostPaymentRequest paymentRequest) {
    return paymentGatewayService.processPayment(toPaymentRequestData(paymentRequest));
  }

  private static PaymentRequestData toPaymentRequestData(PostPaymentRequest paymentRequest) {
    return new PaymentRequestData(paymentRequest.cardNumber(), paymentRequest.expiryMonth(),
        paymentRequest.expiryYear(), paymentRequest.currency(), paymentRequest.amount(),
        paymentRequest.cvv());
  }

  private static PostPaymentResponse toPostPaymentResponse(PaymentData paymentData) {
    return new PostPaymentResponse(paymentData.id(),
        paymentData.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        HttpStatus.OK.value(),
        paymentData.cardNumberLastFour(), paymentData.expiryMonth(), paymentData.expiryYear(),
        paymentData.currency(), paymentData.amount(), null);
  }
}
