
// ------------------------------------------------------------------------------------------------
// ================================================================================================
// PACKAGE DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------
package com.checkout.payment.gateway.model;

// ------------------------------------------------------------------------------------------------
// ================================================================================================
// IMPORT DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------

import com.checkout.payment.gateway.model.validators.AllowedCurrencies;
import com.checkout.payment.gateway.model.validators.ValidExpiryDate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.util.UUID;
// ------------------------------------------------------------------------------------------------
// ================================================================================================
// CLASS DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------


@Data
@ValidExpiryDate
public class PostPaymentRequest implements Serializable {
  /* ********************************************************************************************
   * CLASS VARIABLES
   *********************************************************************************************/

  UUID id;

  @Min(value = 1, message = "Expiry month must be between 1 and 12")
  @Max(value = 12, message = "Expiry month must be between 1 and 12")
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @JsonProperty("expiry_year")
  private int expiryYear;

  @NotNull(message = "Currency is required")
  @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters only")
  @AllowedCurrencies(message = "Currency must be one of: EUR, USD, GBP")
  private String currency;

  @Min(value = 1, message = "Amount must be greater than zero")
  private int amount;

  @Min(value = 100, message = "CVV must be 3 or 4 digits")
  @Max(value = 9999, message = "CVV must be 3 or 4 digits")
  private int cvv;

  @NotNull(message = "Card number is required")
  @Size(min = 14, max = 19, message = "Card number must be 14-19 digits")
  @Pattern(regexp = "^\\d+$", message = "Card number must be digits only")
  @JsonProperty("card_number")
  private String cardNumber;
  /* ********************************************************************************************
   * CONSTRUCTOR METHODS
   *********************************************************************************************/

  /* ********************************************************************************************
   * PRIVATE METHODS
   *********************************************************************************************/

  /* ********************************************************************************************
   * PROTECTED METHODS
   *********************************************************************************************/

  /* ********************************************************************************************
   * PUBLIC METHODS
   *********************************************************************************************/


  public int getCardNumberLastFour() {
    return Integer.parseInt(cardNumber.substring(cardNumber.length() - 4));
  }


  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber=" + cardNumber +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + cvv +
        '}';
  }
}
