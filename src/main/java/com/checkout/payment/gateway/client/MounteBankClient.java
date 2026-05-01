package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.client.dto.MounteBankRequest;
import com.checkout.payment.gateway.client.dto.MounteBankResponse;
import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.shared.exception.MounteBankException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class MounteBankClient {

  private final RestClient mountBankClient;
  private final MounteBankMapper mounteBankMapper;

  @Retry(name = "mounteBank", fallbackMethod = "fallback")
  @CircuitBreaker(name = "mounteBank", fallbackMethod = "fallback")
  public MounteBankResponse executePayment(Payment payment) {
    MounteBankRequest request = mounteBankMapper.toRequest(payment);
    return mountBankClient.post()
        .uri("/payments")
        .body(request)
        .retrieve()
        .body(MounteBankResponse.class);
  }

  private MounteBankResponse fallback(Exception ex) {
    if (ex instanceof HttpClientErrorException) {
      throw (HttpClientErrorException) ex;
    }
    log.error("Bank simulator unavailable", ex);
    throw new MounteBankException(ex);
  }

}
