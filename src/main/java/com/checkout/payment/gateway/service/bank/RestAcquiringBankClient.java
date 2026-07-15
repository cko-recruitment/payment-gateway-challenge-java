package com.checkout.payment.gateway.service.bank;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class RestAcquiringBankClient implements AcquiringBankClient {
  private final RestTemplate restTemplate;
  private String bankPaymentsUrl = "http://localhost:8080/payments";

  public RestAcquiringBankClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Value("${bank.simulator.url:http://localhost:8080/payments}")
  public void setBankPaymentsUrl(String bankPaymentsUrl) {
    this.bankPaymentsUrl = bankPaymentsUrl;
  }

  @Override
  public PaymentStatus processPayment(PostPaymentRequest paymentRequest) {
    BankPaymentRequest bankPaymentRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        String.format("%02d/%d", paymentRequest.getExpiryMonth(), paymentRequest.getExpiryYear()),
        normalizeCurrency(paymentRequest.getCurrency()),
        paymentRequest.getAmount(),
        String.valueOf(paymentRequest.getCvv())
    );

    try {
      ResponseEntity<BankPaymentResponse> bankResponse = restTemplate.postForEntity(
          bankPaymentsUrl,
          bankPaymentRequest,
          BankPaymentResponse.class
      );
      if (bankResponse.getBody() != null && bankResponse.getBody().isAuthorized()) {
        return PaymentStatus.AUTHORIZED;
      }
      return PaymentStatus.DECLINED;
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode().is5xxServerError()) {
        throw new BankUnavailableException("Bank simulator is unavailable");
      }
      throw ex;
    } catch (ResourceAccessException ex) {
      throw new BankUnavailableException("Bank simulator is unavailable");
    }
  }

  private String normalizeCurrency(String currency) {
    if (currency == null) {
      return null;
    }
    return currency.trim().toUpperCase();
  }
}
