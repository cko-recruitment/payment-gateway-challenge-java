package com.checkout.payment.gateway.processor;

import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.BankFailure;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentRequestData;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BankPaymentsProcessor implements PaymentsProcessor {

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

    try {
      response = paymentClient
          .post()
          .uri("/payments")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(bankPaymentRequest)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {
            throw new BankException(BankFailure.ofBankStatus(errorResponse.getStatusCode()),
                "Bank returned " + errorResponse.getStatusCode());
          })
          .body(BankPaymentResponse.class);
    } catch (RestClientException ex) {
      // ResourceAccessException extends this, so one catch covers a refused connection, a timeout
      // and a body we could not parse. BankException is a plain RuntimeException, so the one thrown
      // above for an error status passes straight through.
      throw new BankException(BankFailure.BANK_UNREACHABLE, "Call to the bank failed", ex);
    }

    if (response == null || response.authorized() == null) {
      throw new BankException(BankFailure.BANK_UNREACHABLE,
          "Bank returned no authorization decision");
    }
    return response;
  }

  private static BankPaymentRequest toBankPaymentRequest(PaymentRequestData paymentRequest) {
    return new BankPaymentRequest(paymentRequest.cardNumber(), paymentRequest.getExpiryDate(),
        paymentRequest.currency(),
        paymentRequest.amount(), paymentRequest.cvv());
  }
}
