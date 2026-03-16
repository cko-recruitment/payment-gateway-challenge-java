package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain object to store in the Repository
 */
public class Payment {
  private UUID id;
  private PaymentStatus status;
  private String maskedCardNumber;
  private String currency;
  private Integer amount;
  private Integer expiryMonth;
  private Integer expiryYear;
  private String authorizationCode;
  private Instant createdAt;

  public Payment(UUID id, PaymentStatus status, String maskedCardNumber, String currency,
      Integer amount, Integer expiryMonth, Integer expiryYear, String authorizationCode,
      Instant createdAt) {
    this.id = id;
    this.status = status;
    this.maskedCardNumber = maskedCardNumber;
    this.currency = currency;
    this.amount = amount;
    this.expiryMonth = expiryMonth;
    this.expiryYear = expiryYear;
    this.authorizationCode = authorizationCode;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getMaskedCardNumber() {
    return maskedCardNumber;
  }

  public void setMaskedCardNumber(String maskedCardNumber) {
    this.maskedCardNumber = maskedCardNumber;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }

  @Override
  public String toString() {
    return "Payment{" +
        "id=" + id +
        ", status=" + status +
        ", maskedCardNumber='" + maskedCardNumber + '\'' +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", createdAt=" + createdAt +
        '}';
  }
}
