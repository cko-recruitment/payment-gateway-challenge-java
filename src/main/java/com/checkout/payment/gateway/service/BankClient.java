package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.BankInvalidResponseException;
import com.checkout.payment.gateway.exception.BankRejectedException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.internal.BankResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient {
  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);
  private static final String PAYMENTS_ENDPOINT = "/payments";

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankClient(
      RestTemplate restTemplate,
      @Value("${bank.base-url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }


  public BankResponse processPayment(PostPaymentRequest request) {
    try{
      LOG.info("Processing payment request {}", request);

      return requestWithRetry(request);
    }  catch (HttpServerErrorException ex) {
      LOG.error(ex.getMessage(), ex);

      throw new BankUnavailableException(); // intentionally not exposing stack
    } catch (HttpClientErrorException ex) {
      LOG.error(
          "Invalid Payment request passed gateway validation but was failed by bank with: {}",
          ex.getMessage(),
          ex);

      throw new BankRejectedException();
    } catch (ResourceAccessException ex) {
      LOG.error(
          "Maybe start the simulator!?: {}",
          ex.getMessage(),
          ex);
      throw new BankUnavailableException();
  }
  }

  @Retryable(
      retryFor = HttpServerErrorException.ServiceUnavailable.class,
      maxAttemptsExpression = "${bank.retry.max-attempts}" ,
      backoff = @Backoff(delayExpression = "${bank.retry.delay-ms}")
  )
  @NonNull
  private BankResponse requestWithRetry(PostPaymentRequest request) {
    BankResponse response = restTemplate.postForEntity(
        bankUrl + PAYMENTS_ENDPOINT,
        request,
        BankResponse.class
    ).getBody();

    if (response == null) {
      LOG.error("Received empty response from bank");
      throw new BankInvalidResponseException();
    }
    LOG.info("Received response from bank: {}", response);

    return response;
  }
}