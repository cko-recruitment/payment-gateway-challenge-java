package com.checkout.payment.gateway.security;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecureLogger {

  private final Logger logger;
  private final Map<String, MaskingStrategy> strategies = new HashMap<>();

  public SecureLogger() {
    this.logger = LoggerFactory.getLogger(SecureLogger.class);
    strategies.put("card_number", new CardNumberMaskingStrategy());
    strategies.put("cvv", new CvvMaskingStrategy());
    strategies.put("cardNumber", new CardNumberMaskingStrategy());
  }

  public void info(String message, Object... args) {
    logger.info(message, maskArgs(args));
  }

  public void warn(String message, Object... args) {
    logger.warn(message, maskArgs(args));
  }

  public void error(String message, Object... args) {
    logger.error(message, maskArgs(args));
  }

  public void debug(String message, Object... args) {
    logger.debug(message, maskArgs(args));
  }

  private Object[] maskArgs(Object[] args) {
    Object[] masked = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      masked[i] = maskObject(args[i]);
    }
    return masked;
  }

  private Object maskObject(Object obj) {
    if (obj == null) {
      return null;
    }
    // TODO: Implement reflection-based masking for objects with sensitive fields
    // For now, return toString() which should be secured in the model
    return obj;
  }
}
