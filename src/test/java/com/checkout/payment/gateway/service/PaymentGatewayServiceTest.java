package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentCurrency;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BankSimulatorClient bankSimulatorClient;

    @InjectMocks
    private PaymentGatewayService paymentGatewayService;

    private PostPaymentRequest validRequest;
    private UUID paymentId;
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        validRequest = new PostPaymentRequest(
                "1234567890123456",
                12,
                2026,
                PaymentCurrency.USD,
                100,
                "123"
        );
    }

    @Test
    void shouldReturnPaymentWhenIdIsFound() {
        PostPaymentResponse expectedResponse = new PostPaymentResponse(paymentId, merchantId,
            PaymentStatus.AUTHORIZED, "3456", 12, 2026,
            PaymentCurrency.USD, 100);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(expectedResponse));

        PostPaymentResponse result = paymentGatewayService.getPaymentById(paymentId);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowExceptionWhenPaymentIdIsNotFound() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(EventProcessingException.class, () -> paymentGatewayService.getPaymentById(paymentId));
    }

    @Test
    void shouldSaveAndReturnAuthorizedWhenPaymentIsSuccessful() {
        when(paymentRepository.saveIfAbsent(any()))
            .thenReturn(Optional.empty());
        when(bankSimulatorClient.processPayment(any()))
            .thenReturn(new BankPaymentResponse(true, "auth-code"));

        PostPaymentResponse result = paymentGatewayService.processPayment(validRequest, paymentId, merchantId);

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.cardNumberLastFour()).isEqualTo("3456");
        verify(paymentRepository).save(any(PostPaymentResponse.class));
    }

    @Test
    void shouldSaveAndReturnDeclinedWhenBankDeclines() {
        when(paymentRepository.saveIfAbsent(any()))
            .thenReturn(Optional.empty());
        when(bankSimulatorClient.processPayment(any()))
            .thenReturn(new BankPaymentResponse(false, null));

        PostPaymentResponse result = paymentGatewayService.processPayment(validRequest, paymentId, merchantId);

        assertThat(result.status()).isEqualTo(PaymentStatus.DECLINED);
        verify(paymentRepository).save(any(PostPaymentResponse.class));
    }

    @Test
    void shouldReturnRejectedWhenBankFailsWithClientError() {
        when(paymentRepository.saveIfAbsent(any())).thenReturn(Optional.empty());
        when(bankSimulatorClient.processPayment(any())).thenThrow(
                WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null));

        PostPaymentResponse result = paymentGatewayService.processPayment(validRequest, paymentId, merchantId);

        assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
        verify(paymentRepository).save(any(PostPaymentResponse.class));
    }

    @Test
    void shouldThrowConflictWhenPaymentIsAlreadyProcessing() {
        PostPaymentResponse processingResponse = new PostPaymentResponse(paymentId, merchantId, PaymentStatus.PROCESSING, "3456", 12, 2026, PaymentCurrency.USD, 100);
        when(paymentRepository.saveIfAbsent(any())).thenReturn(Optional.of(processingResponse));

        assertThrows(EventProcessingException.class, () -> paymentGatewayService.processPayment(validRequest, paymentId, merchantId));
        verifyNoInteractions(bankSimulatorClient);
    }

    @Test
    void shouldReturnCachedResponseWhenPaymentAlreadyCompleted() {
        PostPaymentResponse completedResponse = new PostPaymentResponse(paymentId, merchantId, PaymentStatus.AUTHORIZED, "3456", 12, 2026, PaymentCurrency.USD, 100);
        when(paymentRepository.saveIfAbsent(any())).thenReturn(Optional.of(completedResponse));

        PostPaymentResponse result = paymentGatewayService.processPayment(validRequest, paymentId, merchantId);

        assertThat(result).isEqualTo(completedResponse);
        verifyNoInteractions(bankSimulatorClient);
    }
}
