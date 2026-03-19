package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.YearMonth;

public record ExpiryDate(int month, int year) {

  @JsonCreator
  public static ExpiryDate of(
      @JsonProperty("expiry_month") int month,
      @JsonProperty("expiry_year") int year) {
    return new ExpiryDate(month, year);
  }

  public ExpiryDate {
    if (month < 1 || month > 12)
      throw new IllegalArgumentException("Expiry month must be between 1 and 12");
    if (year < 1000 || year > 9999)
      throw new IllegalArgumentException("Expiry year must be 4 digits");
    if (YearMonth.of(year, month).isBefore(YearMonth.now()))
      throw new IllegalArgumentException("Card expiryDate date must be in the future");
  }

  public String getPlainExpiryDate() {
    return month + "/" + year;
  }
}
