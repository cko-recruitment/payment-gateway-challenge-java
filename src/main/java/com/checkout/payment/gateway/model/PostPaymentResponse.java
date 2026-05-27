package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.GatewayCode;
import com.checkout.payment.gateway.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Response for payment processing and retrieval")
public class PostPaymentResponse extends BasePaymentResponse {

  @Schema(description = "Unique payment ID", example = "7d474a2e-4748-463c-ad64-670045457a6e")
  private UUID id;

  @Schema(description = "Payment status", example = "Authorized")
  private PaymentStatus status;

  @Schema(description = "Last four digits of the card", example = "4242")
  private Integer cardNumberLastFour;

  @Schema(description = "Expiry month", example = "12")
  private Integer expiryMonth;

  @Schema(description = "Expiry year", example = "2027")
  private Integer expiryYear;

  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @Schema(description = "Amount in minor units", example = "100")
  private Integer amount;

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

  public Integer getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(Integer cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
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

  public static PostPaymentResponse rejected(String description) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setStatus(PaymentStatus.REJECTED);
    response.setDescription(description);
    response.setGatewayCode(GatewayCode.INVALID_REQUEST);
    response.addLink(new Link("self", "/api/v1/payments", "POST"));
    return response;
  }

  public static PostPaymentResponse declined(String description) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setStatus(PaymentStatus.DECLINED);
    response.setDescription(description);
    response.setGatewayCode(GatewayCode.DECLINED);
    response.addLink(new Link("self", "/api/v1/payments", "POST"));
    return response;
  }

  @Override
  public String toString() {
    return "PostPaymentResponse{" +
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
