package com.checkout.payment.gateway.security;

public class CardNumberMaskingStrategy implements MaskingStrategy {

  @Override
  public String mask(String value) {
    if (value == null || value.length() < 4) {
      return "****";
    }
    return "****" + value.substring(value.length() - 4);
  }
}
