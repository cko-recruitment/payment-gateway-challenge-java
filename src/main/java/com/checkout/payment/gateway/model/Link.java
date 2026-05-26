package com.checkout.payment.gateway.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * HATEOAS-style link for suggesting next actions.
 */
@Schema(description = "HATEOAS link for next action")
public class Link {

  @Schema(description = "Link relation type", example = "self")
  private String rel;

  @Schema(description = "URL path", example = "/api/v1/payments/7d474a2e-4748-463c-ad64-670045457a6e")
  private String href;

  @Schema(description = "HTTP method", example = "GET")
  private String method;

  public Link() {
  }

  public Link(String rel, String href, String method) {
    this.rel = rel;
    this.href = href;
    this.method = method;
  }

  public String getRel() {
    return rel;
  }

  public void setRel(String rel) {
    this.rel = rel;
  }

  public String getHref() {
    return href;
  }

  public void setHref(String href) {
    this.href = href;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }
}
