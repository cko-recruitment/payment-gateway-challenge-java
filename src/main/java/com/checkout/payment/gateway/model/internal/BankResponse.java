package com.checkout.payment.gateway.model.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

@SuppressWarnings("unused")
public class BankResponse implements Serializable {
  private boolean authorized;

  @JsonProperty("authorization_code")
  private String authorizationCode;

  public boolean isAuthorized() {
    return authorized;
  }

  public void setAuthorized(boolean authorized) {
    this.authorized = authorized;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }
}
