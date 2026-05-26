package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.GatewayCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Base response shared by all API responses")
public class BasePaymentResponse {

  @Schema(description = "Machine-readable outcome code", example = "SUCCESS")
  private String code;

  @Schema(description = "Human-readable description of the result", example = "Payment authorized successfully")
  private String description;

  @Schema(description = "Recommended client action: none, retry, fix_request, contact_support", example = "none")
  private String action;

  @Schema(description = "HATEOAS links for next actions")
  private List<Link> links;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public List<Link> getLinks() {
    return links;
  }

  public void setLinks(List<Link> links) {
    this.links = links;
  }

  public void addLink(Link link) {
    if (this.links == null) {
      this.links = new ArrayList<>();
    }
    this.links.add(link);
  }

  public void setGatewayCode(GatewayCode gatewayCode) {
    this.code = gatewayCode.getCode();
    if (this.description == null || this.description.isBlank()) {
      this.description = gatewayCode.getDescription();
    }
    this.action = gatewayCode.getAction();
  }
}
