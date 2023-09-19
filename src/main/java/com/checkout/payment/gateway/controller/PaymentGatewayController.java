package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.RequestEvent;
import com.checkout.payment.gateway.model.ResponseEvent;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @GetMapping("/ok")
  public ResponseEntity<ResponseEvent> okRequest(RequestEvent event) {
    return new ResponseEntity<>(paymentGatewayService.processRequest(event, true), HttpStatus.OK);
  }

  @GetMapping("/nok")
  public ResponseEntity<ResponseEvent> notRequest(RequestEvent event) {
    return new ResponseEntity<>(paymentGatewayService.processRequest(event, false), HttpStatus.OK);
  }
}
