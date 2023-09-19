package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.RequestEvent;
import com.checkout.payment.gateway.model.ResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {
  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  public ResponseEvent processRequest(RequestEvent event, boolean ok) {
    LOG.info("Processing event {}", event);

    if (!ok) {
      throw new EventProcessingException("Wupssss...");
    }

    return new ResponseEvent(event.getMessage());
  }

}
