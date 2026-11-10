package com.checkout.payment.gateway.controller;

/**
 * Example payloads rendered in the OpenAPI document. They are compile-time constants so they can
 * be referenced from annotations, and they are kept out of the controller to keep it readable.
 *
 * <p>Every example here was taken from a real response, so it shows the exact shape a caller
 * gets. The card numbers are the ones the bundled bank simulator recognises: one ending in an odd
 * digit is authorized, one ending in an even digit is declined.
 */
final class ApiExamples {

  static final String REQUEST_AUTHORIZED = """
      {
        "card_number": "2222405343248877",
        "expiry_month": 4,
        "expiry_year": 2030,
        "currency": "GBP",
        "amount": 10500,
        "cvv": "123"
      }""";

  static final String REQUEST_DECLINED = """
      {
        "card_number": "2222405343248112",
        "expiry_month": 4,
        "expiry_year": 2030,
        "currency": "GBP",
        "amount": 10500,
        "cvv": "123"
      }""";

  static final String RESPONSE_AUTHORIZED = """
      {
        "id": "f702c53c-ca49-4159-8af4-958aa027728e",
        "payment_status": "Authorized",
        "status": 200,
        "card_number_last_four": "8877",
        "expiry_month": 4,
        "expiry_year": 2030,
        "currency": "GBP",
        "amount": 10500
      }""";

  static final String RESPONSE_DECLINED = """
      {
        "id": "93bcbb4f-f7db-49df-91d0-e2a61b49cb89",
        "payment_status": "Declined",
        "status": 200,
        "card_number_last_four": "8112",
        "expiry_month": 4,
        "expiry_year": 2030,
        "currency": "GBP",
        "amount": 10500
      }""";

  static final String ERROR_VALIDATION = """
      {
        "type": "about:blank",
        "title": "Validation failed",
        "status": 400,
        "detail": "One or more fields are invalid",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": [
          {
            "field": "card_number",
            "message": "must contain digits only",
            "rejected_value": "***"
          },
          {
            "field": "expiry_year",
            "message": "must be greater than or equal to 1000",
            "rejected_value": 30
          }
        ]
      }""";

  static final String ERROR_EXPIRED_CARD = """
      {
        "type": "about:blank",
        "title": "Validation failed",
        "status": 400,
        "detail": "One or more fields are invalid",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": [
          {
            "field": "request",
            "message": "card has expired: the expiry month and year are in the past",
            "rejected_value": null
          }
        ]
      }""";

  static final String ERROR_MALFORMED = """
      {
        "type": "about:blank",
        "title": "Malformed request",
        "status": 400,
        "detail": "Invalid value for 'amount'",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_INVALID_ID = """
      {
        "type": "about:blank",
        "title": "Malformed request",
        "status": 400,
        "detail": "Invalid value for 'id'",
        "instance": "/payment/not-a-uuid",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_NOT_FOUND = """
      {
        "type": "about:blank",
        "title": "Payment not found",
        "status": 404,
        "detail": "No payment found with ID 3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "instance": "/payment/3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "error": []
      }""";

  static final String ERROR_METHOD_NOT_ALLOWED = """
      {
        "type": "about:blank",
        "title": "Malformed request",
        "status": 405,
        "detail": "Method 'PUT' is not supported for this endpoint",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_UNSUPPORTED_MEDIA_TYPE = """
      {
        "type": "about:blank",
        "title": "Malformed request",
        "status": 415,
        "detail": "Content type 'text/plain;charset=UTF-8' is not supported",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_BANK_REJECTED = """
      {
        "type": "about:blank",
        "title": "Request failed",
        "status": 502,
        "detail": "The acquiring bank rejected the request from this gateway.",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_BANK_UNREACHABLE = """
      {
        "type": "about:blank",
        "title": "Request failed",
        "status": 502,
        "detail": "The acquiring bank could not be reached.",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_BANK_UNAVAILABLE = """
      {
        "type": "about:blank",
        "title": "Request failed",
        "status": 503,
        "detail": "The acquiring bank is currently unavailable.",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  static final String ERROR_INTERNAL = """
      {
        "type": "about:blank",
        "title": "Request failed",
        "status": 500,
        "detail": "The request could not be processed",
        "instance": "/payment",
        "payment_status": "Rejected",
        "error": []
      }""";

  private ApiExamples() {
  }
}
