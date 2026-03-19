package com.checkout.payment.gateway.util;

public final class CardUtils {
  private CardUtils() {}

  public static String getLastFourCardDigits(String cardNumber) {
    if (cardNumber != null && cardNumber.length() >= 4) {
      return cardNumber.substring(cardNumber.length() - 4);
    }
    return "";
  }
}
