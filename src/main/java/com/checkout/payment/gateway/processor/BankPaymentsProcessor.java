package com.checkout.payment.gateway.processor;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentRequestData;
import com.checkout.payment.gateway.properties.BankProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BankPaymentsProcessor implements PaymentsProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(BankPaymentsProcessor.class);

  private final RestClient paymentClient;

  public BankPaymentsProcessor(BankProperties bankProperties) {
    LOG.info("baseUrl {}", bankProperties.apiUrl());
    this.paymentClient = RestClient.builder().baseUrl(bankProperties.apiUrl()).build();
  }

  @Override
  public BankPaymentResponse process(PaymentRequestData paymentRequestData) {
    return process(toBankPaymentRequest(paymentRequestData));
  }

  public BankPaymentResponse process(BankPaymentRequest bankPaymentRequest) {
    BankPaymentResponse bankPaymentResponse = paymentClient

        .post()
        .uri("/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .body(bankPaymentRequest)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError, (req, res) -> {
              throw new IllegalArgumentException(
                  "Bad request: " + new String(res.getBody().readAllBytes()));
            })
        .body(BankPaymentResponse.class);

    return bankPaymentResponse;
  }
  
  private static BankPaymentRequest toBankPaymentRequest(PaymentRequestData paymentRequest) {
    return new BankPaymentRequest(paymentRequest.cardNumber(), paymentRequest.getExpiryDate(),
        paymentRequest.currency(),
        paymentRequest.amount(), paymentRequest.cvv());
  }
}
