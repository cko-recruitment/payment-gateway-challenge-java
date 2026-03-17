package com.checkout.payment.gateway.utils;

import com.checkout.payment.gateway.model.PaymentRequest;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility component responsible for generating a deterministic fingerprint
 * for a given {@link PaymentRequest}.
 */
@Component
public class PaymentRequestFingerPrintGenerator {

  public String fingerprint(PaymentRequest request) {
    String raw = String.join("|",
        safe(request.getCardNumber()),
        String.valueOf(request.getExpiryMonth()),
        String.valueOf(request.getExpiryYear()),
        safe(request.getCurrency()),
        String.valueOf(request.getAmount()),
        safe(request.getCvv())
    );

    return sha256(raw);
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm not available", ex);
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (byte b : bytes) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }
}
