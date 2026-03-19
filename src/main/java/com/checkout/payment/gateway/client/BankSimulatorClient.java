package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
public class BankSimulatorClient {

    private final WebClient webClient;

    public BankSimulatorClient(WebClient.Builder webClientBuilder,
                               @Value("${bank.simulator.url:http://localhost:8080/payments}") String bankSimulatorUrl) {
        this.webClient = webClientBuilder
                .baseUrl(bankSimulatorUrl)
                .build();
    }

    public BankPaymentResponse processPayment(BankPaymentRequest request) {
        log.debug("Initiating upstream payment request to bank simulator. Amount: {} {}", request.amount(), request.currency());
        
        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), BankPaymentRequest.class)
                .retrieve()
                .bodyToMono(BankPaymentResponse.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .filter(this::isRetryable)
                        .doBeforeRetry(retrySignal -> log.warn("Transient bank simulator failure. Attempt: {} (max: 4). Retrying...", retrySignal.totalRetries() + 1))
                )
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().is4xxClientError()) {
                        log.warn("Bank simulator rejected request with client error {}: {}. Request payload: {}", ex.getStatusCode(), ex.getResponseBodyAsString(), request);
                    } else {
                        log.error("Critical upstream bank simulator failure {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    }
                    return Mono.error(ex);
                })
                .block();
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof java.net.ConnectException || 
               throwable instanceof java.util.concurrent.TimeoutException ||
               throwable instanceof io.netty.handler.timeout.ReadTimeoutException ||
               throwable instanceof io.netty.channel.ConnectTimeoutException;
    }
}
