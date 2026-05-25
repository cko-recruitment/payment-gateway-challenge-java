package com.checkout.payment.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecureLogger {

  private final Logger logger;

  public SecureLogger() {
    this.logger = LoggerFactory.getLogger(SecureLogger.class);
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
    return obj;
  }
}
