package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

/**
 * Payment Response entity received from the payment gateway
 * for AUTHORIZED / DECLINED payments
 */
public class PaymentResponse {
  private UUID id;
  private PaymentStatus status;
  private String maskedCardNumber;
  private Integer expiryMonth;
  private Integer expiryYear;
  private String currency;
  private Integer amount;

  public PaymentResponse(){}

  public PaymentResponse(UUID id, PaymentStatus status, String maskedCardNumber,
      Integer expiryMonth, Integer expiryYear, String currency, Integer amount) {
    this.id = id;
    this.status = status;
    this.maskedCardNumber = maskedCardNumber;
    this.expiryMonth = expiryMonth;
    this.expiryYear = expiryYear;
    this.currency = currency;
    this.amount = amount;
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

  public String getCardNumberLastFour() {
    return maskedCardNumber;
  }

  public void setCardNumberLastFour(String cardNumberLastFour) {
    this.maskedCardNumber = cardNumberLastFour;
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


  @Override
  public String toString() {
    return "GetPaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + maskedCardNumber +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
