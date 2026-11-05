package com.checkout.payment.gateway.processor;

import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.BankFailure;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentRequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BankPaymentsProcessor implements PaymentsProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(BankPaymentsProcessor.class);

  private final RestClient paymentClient;

  public BankPaymentsProcessor(RestClient bankRestClient) {
    this.paymentClient = bankRestClient;
  }

  @Override
  public BankPaymentResponse process(PaymentRequestData paymentRequestData) {
    return process(toBankPaymentRequest(paymentRequestData));
  }

  public BankPaymentResponse process(BankPaymentRequest bankPaymentRequest) {
    BankPaymentResponse response;

    // The request masks the PAN and the CVV when printed, so it is safe to log.
    LOG.debug("POST /payments to the bank with {}", bankPaymentRequest);

    try {
      response = paymentClient
          .post()
          .uri("/payments")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(bankPaymentRequest)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {
            LOG.warn("The bank responded {} to POST /payments", errorResponse.getStatusCode());
            throw new BankException(BankFailure.ofBankStatus(errorResponse.getStatusCode()),
                "Bank returned " + errorResponse.getStatusCode());
          })
          .body(BankPaymentResponse.class);
    } catch (RestClientException ex) {
      LOG.warn("Could not complete POST /payments to the bank: {}", ex.getMessage());
      throw new BankException(BankFailure.BANK_UNREACHABLE, "Call to the bank failed", ex);
    }

    if (response == null || response.authorized() == null) {
      LOG.warn("The bank responded to POST /payments without an authorization decision");
      throw new BankException(BankFailure.BANK_UNREACHABLE,
          "Bank returned no authorization decision");
    }
    LOG.debug("The bank responded to POST /payments with authorized={}", response.authorized());
    return response;
  }

  private static BankPaymentRequest toBankPaymentRequest(PaymentRequestData paymentRequest) {
    return new BankPaymentRequest(paymentRequest.cardNumber(), paymentRequest.getExpiryDate(),
        paymentRequest.currency(),
        paymentRequest.amount(), paymentRequest.cvv());
  }
}
