package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.Client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankingClientResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.utils.BankingClientRequestMapper;
import com.checkout.payment.gateway.utils.CardNumberMaskingUtil;
import com.checkout.payment.gateway.utils.PaymentResponseMapper;
import com.checkout.payment.gateway.validator.PaymentBusinessValidator;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final AcquiringBankClient client;
  private final PaymentBusinessValidator validator;
  private final BankingClientRequestMapper bankingClientRequestMapper;
  private final PaymentResponseMapper paymentResponseMapper;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, AcquiringBankClient client,
      PaymentBusinessValidator validator, BankingClientRequestMapper bankingClientRequestMapper,
      PaymentResponseMapper paymentResponseMapper) {
    this.paymentsRepository = paymentsRepository;
    this.client = client;
    this.validator = validator;
    this.bankingClientRequestMapper = bankingClientRequestMapper;
    this.paymentResponseMapper = paymentResponseMapper;
  }

  public PaymentResponse getPaymentById(UUID paymentId) {
    Payment payment = paymentsRepository.get(paymentId)
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));
    return paymentResponseMapper.map(payment);
  }

  public PaymentResponse processPayment(PaymentRequest request) {
    validator.validate(request);

    BankingClientResponse bankResponse =
        client.authorize(bankingClientRequestMapper.map(request)); //Acquiring bank client call

    Payment payment = buildPayment(request, bankResponse);

    Payment savedPayment = paymentsRepository.add(payment);

    return paymentResponseMapper.map(savedPayment);
  }

  private Payment buildPayment(PaymentRequest request, BankingClientResponse bankResponse) {
    PaymentStatus status = bankResponse.getAuthorized()
        ? PaymentStatus.AUTHORIZED
        : PaymentStatus.DECLINED;

    String authorizationCode = bankResponse.getAuthorized()
        ? bankResponse.getAuthorizationCode()
        : null;

    return new Payment(
        UUID.randomUUID(),
        status,
        CardNumberMaskingUtil.mask(request.getCardNumber()),
        request.getCurrency(),
        request.getExpiryMonth(),
        request.getExpiryYear(),
        request.getAmount(),
        authorizationCode,
        Instant.now()
    );
  }

//  private String extractLastFour(String cardNumber) {
//    return cardNumber.substring(cardNumber.length() - 4);
//  }
}
