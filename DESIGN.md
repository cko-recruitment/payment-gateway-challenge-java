# Design Considerations and Assumptions

## Overview

This document describes the implementation of the Payment Gateway API and the core design principles ensuring consistency, security, and developer experience.

---

## 1. Architecture and Layering

The application follows a layered architecture to ensure separation of concerns and testability:

```
Controller (HTTP) → Service (Business Logic) → Repository (Storage)
                                          ↘
                                           BankClient (External API)
```

**Key Responsibilities:**
- **`PaymentGatewayController`**: Thin layer for request routing and resource orchestration. It maintains the "200-Only" API contract.
- **`PaymentGatewayService`**: The core engine for validation (ISO 4217 compliant), bank interaction, and persistence orchestration.
- **`CommonExceptionHandler`**: Global fallback that catches unexpected system errors and translates them into standardized, structured 200-OK responses.
- **`IdempotencyRepository`**: Dual-indexed in-memory storage (System ID and Idempotency Key) with merchant isolation.

---

## 2. API Design Principles

### Decision: The "200-Only" RESTful API

To provide a superior integration experience, the API avoids varied HTTP status codes for business or system outcomes. Instead, it uses **HTTP 200 OK** for all successful transport scenarios, conveying the actual result within a structured JSON body.

**Rationale:**
- **Simplified Client Logic**: Clients only need to handle a single response parsing flow.
- **Unified Error Model**: Every response (Success, Bank Decline, Validation Error, or System Crash) shares the same base structure.

### Decision: Unified `GatewayCode` Metadata

All API responses inherit from `BasePaymentResponse`, providing machine-readable outcome metadata:

| Field | Description | Example |
| :--- | :--- | :--- |
| `code` | Unique, machine-readable outcome code. | `PAYMENT_DECLINED` |
| `message` | Human-readable description of the result. | "Insufficient funds" |
| `retryable` | Boolean indicating if a retry is safe/recommended. | `true` |

**Standardized Gateway Codes:**
- `SUCCESS`: Operation completed as requested.
- `PAYMENT_DECLINED`: Bank refused the payment (Final state).
- `INVALID_REQUEST`: Validation failed or missing parameters (Final state).
- `BANK_UNAVAILABLE`: External bank downtime (Retryable).
- `PAYMENT_NOT_FOUND`: Retrieval requested for a non-existent resource.
- `INTERNAL_ERROR`: Unexpected gateway failure (Retryable).

---

## 3. Retrieval Strategy

### Decision: Dual Canonical URIs

The API supports two complementary retrieval paths, both returning a `GetPaymentResponse`:

1.  **`GET /api/v1/payments/{id}`**: Retrieval via system-generated UUID.
2.  **`GET /api/v1/payments/idempotency/{key}`**: Retrieval via the merchant's original reference (Idempotency Key).

**Contextual Security:**
Both endpoints require the `Merchant-Id` header. Data is scoped strictly to the provided merchant, ensuring that merchants cannot retrieve each other's data even if they guess an ID or Key.

---

## 4. Idempotency and Validation

### Decision: Hardened Idempotency
`POST /api/v1/payments` uses the `Idempotency-Key` to ensure that duplicate requests from a merchant (e.g., due to network retries) do not result in multiple bank charges. Successful or declined responses are cached and returned for subsequent requests with the same key.

### Decision: Strict ISO 4217 Validation
Currency validation is not merely format-based. The system utilizes `java.util.Currency` to ensure that every request contains a globally valid ISO currency code, preventing downstream failures at the bank level.

---

## 5. Storage and Performance

### Decision: Dual-Indexed In-Memory Repository
The `InMemoryIdempotencyRepository` maintains two internal hash maps (System ID and Idempotency Key). This provides **O(1)** retrieval performance for all `GET` operations while keeping the architecture simple for the current scope.

---

## 6. Assumptions and Limitations

1.  **Volatility**: Data is lost on application restart (In-memory storage).
2.  **Context Trust**: `Merchant-Id` is trusted from the header without cryptographic verification (Exercise scope).
3.  **Synchronous Flow**: Requests wait for the bank's response. Future versions would implement async webhooks.

---

## 7. Design Trade-offs Summary

| Trade-off | Choice | Rationale |
| :--- | :--- | :--- |
| **Status Codes** | 200-Only | Predictable client parsing and business outcome focus. |
| **Response Model** | Inheritance | `GetPaymentResponse` extends `PostPaymentResponse` for semantic clarity without code duplication. |
| **Versioning** | Path (/v1) | Safe evolution and backward compatibility. |
| **Logging** | Masking | PII protection implemented via `toString()` overrides in models. |
