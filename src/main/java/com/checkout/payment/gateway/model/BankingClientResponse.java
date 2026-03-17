package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Acquiring Bank Client Response entity
 */
public class BankingClientResponse {

  @JsonProperty("authorized")
  private Boolean authorized;
  @JsonProperty("authorization_code")
  private String authorizationCode;

  public BankingClientResponse () {}

  public BankingClientResponse(
      Boolean authorized,
      String authorizationCode
  ) {
    this.authorizationCode = authorizationCode;
    this.authorized = authorized;
  }

  public Boolean getAuthorized() {
    return authorized;
  }

  public void setAuthorized(Boolean authorized) {
    this.authorized = authorized;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }
  @Override
  public String toString() {
    return "BankingClientResponse{" +
        "authorized=" + authorized +
        ", authorizationCode='" + authorizationCode + '\'' +
        '}';
  }
}
