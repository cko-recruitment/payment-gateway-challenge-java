package com.checkout.payment.gateway.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SuppressWarnings("unused")
public class PostPaymentRequestObjectNodeBuilder {

  private static final String DEFAULT_CARD_NUMBER = "124567812345678";
  private static final int DEFAULT_EXPIRY_MONTH = 12;
  private static final int DEFAULT_EXPIRY_YEAR = 2222;
  private static final String DEFAULT_CURRENCY = "USD";
  private static final int DEFAULT_AMOUNT = 42;
  private static final String DEFAULT_CVV = "000";

  public final ObjectNode request = validRequest();

  public static ObjectNode validRequest() {
    var node = new ObjectMapper().createObjectNode();
    node.put("card_number", DEFAULT_CARD_NUMBER);
    node.put("expiry_month", DEFAULT_EXPIRY_MONTH);
    node.put("expiry_year", DEFAULT_EXPIRY_YEAR);
    node.put("currency", DEFAULT_CURRENCY);
    node.put("amount", DEFAULT_AMOUNT);
    node.put("cvv", DEFAULT_CVV);

    return node;
  }

  public ObjectNode build() {
    return request;
  }

  public PostPaymentRequestObjectNodeBuilder withCardNumber(String cardNumber) {
    if (cardNumber == null) {
      request.remove("card_number");
    } else {
      request.put("card_number", cardNumber);
    }

    return this;
  }

  public PostPaymentRequestObjectNodeBuilder withExpiryMonth(Integer expiryMonth) {
    if (expiryMonth == null) {
      request.remove("expiry_month");
    } else {
      request.put("expiry_month", expiryMonth);
    }

    return this;
  }

  public PostPaymentRequestObjectNodeBuilder withExpiryYear(Integer expiryYear) {
    if (expiryYear == null) {
      request.remove("expiry_year");
    } else {
      request.put("expiry_year", expiryYear);
    }

    return this;
  }

  public PostPaymentRequestObjectNodeBuilder withCurrency(String currency) {
    if (currency == null) {
      request.remove("currency");
    } else {
      request.put("currency", currency);
    }

    return this;
  }

  public PostPaymentRequestObjectNodeBuilder withAmount(Double amount) {
    if (amount == null) {
      request.remove("amount");
    } else if (amount == Math.floor(amount)) {
      request.put("amount", (int) Math.floor(amount));
    }
    else {
      request.put("amount", amount);
    }

    return this;
  }

  public PostPaymentRequestObjectNodeBuilder withCvv(String cvv) {
    if (cvv == null) {
      request.remove("cvv");
    } else {
      request.put("cvv", cvv);
    }

    return this;
  }
}
