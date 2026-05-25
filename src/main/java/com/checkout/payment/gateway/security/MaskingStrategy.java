package com.checkout.payment.gateway.security;

public interface MaskingStrategy {

  String mask(String value);
}
