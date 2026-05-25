package com.checkout.payment.gateway.security;

public class CvvMaskingStrategy implements MaskingStrategy {

  @Override
  public String mask(String value) {
    if (value == null) {
      return "***";
    }
    return "*".repeat(value.length());
  }
}
