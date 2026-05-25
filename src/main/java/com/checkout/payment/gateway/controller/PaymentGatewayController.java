package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;

@RestController
@RequestMapping("/api")
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @GetMapping("/hello")
  public ResponseEntity<String> hello() {
    return new ResponseEntity<>("Hello, world!", HttpStatus.OK);
  }

  @PostMapping("/payment")
  public ResponseEntity<PostPaymentResponse> processPayment(@RequestBody PostPaymentRequest paymentRequest) {
    try {
      return new ResponseEntity<>(paymentGatewayService.processPayment(paymentRequest), HttpStatus.OK);
    } catch (PaymentValidationException ex) {
      LOG.warn("Payment request rejected: {}", ex.getMessage());
      return new ResponseEntity<>(PostPaymentResponse.rejected(ex.getMessage()), HttpStatus.OK);
    } catch (HttpServerErrorException ex) {
      if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        LOG.error("Bank returned 503 Service Unavailable", ex);
        return new ResponseEntity<>(PostPaymentResponse.declined("Bank service temporarily unavailable"), HttpStatus.OK);
      }
      LOG.error("Bank returned error: {}", ex.getStatusCode(), ex);
      return new ResponseEntity<>(PostPaymentResponse.declined("Bank error: " + ex.getStatusCode()), HttpStatus.OK);
    } catch (BankProcessingException ex) {
      LOG.error("Bank processing failed", ex);
      return new ResponseEntity<>(PostPaymentResponse.declined("Bank processing failed: " + ex.getMessage()), HttpStatus.OK);
    }
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
