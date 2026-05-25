package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.internal.BankResponse;
import java.util.UUID;

public class PaymentResponse {
  private UUID id;
  private PaymentStatus status;
  private String cardNumberLastFour;
  private int expiryMonth;
  private int expiryYear;
  private String currency;
  private int amount;

  public static PaymentResponse of(PostPaymentRequest paymentRequest, BankResponse bankResponse) {
    var postPaymentResponse = new PaymentResponse();

    postPaymentResponse.setId(UUID.randomUUID());
    postPaymentResponse.setStatus(
        bankResponse.isAuthorized()? PaymentStatus.AUTHORIZED: PaymentStatus.DECLINED
    );

    postPaymentResponse.setCardNumberLastFour(paymentRequest.extractLastFour());
    postPaymentResponse.setExpiryMonth(paymentRequest.getExpiryMonth());
    postPaymentResponse.setExpiryYear(paymentRequest.getExpiryYear());
    postPaymentResponse.setCurrency(paymentRequest.getCurrency());
    postPaymentResponse.setAmount(paymentRequest.getAmount());

    return postPaymentResponse;
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
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(String cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  @Override
  public String toString() {
    return "GetPaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
