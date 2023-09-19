package com.checkout.payment.gateway.exception;

public class EventProcessingException extends RuntimeException{
  public EventProcessingException(String message) {
    super(message);
  }
}
