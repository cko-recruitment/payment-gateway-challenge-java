package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.security.SecureLogger;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorClient implements BankClient {

  private final SecureLogger secureLogger;
  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankSimulatorClient(SecureLogger secureLogger, RestTemplate restTemplate, @Value("${bank.url}") String bankUrl) {
    this.secureLogger = secureLogger;
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("card_number", paymentRequest.getCardNumber());
    requestBody.put("expiry_date", paymentRequest.getExpiryDate());
    requestBody.put("currency", paymentRequest.getCurrency() != null ? paymentRequest.getCurrency().toUpperCase() : null);
    requestBody.put("amount", paymentRequest.getAmount());
    requestBody.put("cvv", paymentRequest.getCvv());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

    try {
      secureLogger.info("Calling bank simulator: {}", paymentRequest);
      ResponseEntity<BankPaymentResponse> response =
          restTemplate.postForEntity(bankUrl, httpEntity, BankPaymentResponse.class);
      BankPaymentResponse body = response.getBody();
      if (body == null) {
        throw new BankProcessingException("Bank returned an empty response");
      }
      secureLogger.info("Bank response: authorized={}, code={}", body.isAuthorized(), body.getAuthorizationCode());
      return body;
    } catch (HttpStatusCodeException ex) {
      String message = "Bank returned status " + ex.getStatusCode();
      secureLogger.error(message, ex);
      throw ex;
    } catch (BankProcessingException ex) {
      // Re-throw directly — not a connection failure
      throw ex;
    } catch (Exception ex) {
      secureLogger.error("Bank call failed", ex);
      throw new BankProcessingException("Cannot reach acquiring bank", ex);
    }
  }
}
