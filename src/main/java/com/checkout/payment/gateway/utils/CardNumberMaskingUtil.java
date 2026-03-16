package com.checkout.payment.gateway.utils;

public final class CardNumberMaskingUtil {

  private CardNumberMaskingUtil() {
  }

  public static String mask(String cardNumber) {
    String last4 = last4(cardNumber);
    return "**** **** **** " + last4;
  }

  public static String last4(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < 4) {
      throw new IllegalArgumentException("Invalid card number");
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }

}
