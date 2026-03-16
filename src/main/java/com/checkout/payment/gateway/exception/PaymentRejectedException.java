package com.checkout.payment.gateway.exception;

import java.util.List;

public class PaymentRejectedException extends RuntimeException {

  private final List<String> messages;

  public PaymentRejectedException(List<String> messages) {
    super("Payment request rejected");
    this.messages = List.copyOf(messages);
  }

  public List<String> getMessages() {
    return messages;
  }

}
