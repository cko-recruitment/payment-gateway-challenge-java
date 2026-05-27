# Design Considerations and Assumptions

## Overview

This document describes the implementation of the Payment Gateway API and the core design principles ensuring consistency, security, and developer experience.

---

## 1. Architecture and Layering

The application follows a layered architecture to ensure separation of concerns and testability:

```
Controller (HTTP) → Service (Business Logic) → Repository (Storage)
       ↓                    ↓                        ↘
  RateLimitAspect    PaymentMetricsAspect         BankClient (External API)
```

**Key Responsibilities (following SOLID principles):**
- **`PaymentGatewayController`**: Thin layer for request routing and HTTP response orchestration. Maintains the "200-Only" API contract. Delegates all business logic to Service. *(Single Responsibility Principle)*
- **`PaymentGatewayApi`**: Interface containing all OpenAPI/Swagger annotations. Separates API documentation from implementation to keep Controller readable. *(Interface Segregation Principle)*
- **`PaymentGatewayService`**: The core engine for validation, bank interaction, and persistence orchestration. Depends on abstractions (`BankClient`, `IdempotencyRepository`) rather than concrete implementations. Handles business exceptions and converts them to domain responses. *(Dependency Inversion Principle)*
- **`IdempotencyRepository`**: Interface defining the contract for dual-indexed storage (System ID and Idempotency Key) with merchant isolation. `InMemoryIdempotencyRepository` provides the concrete in-memory implementation. *(Interface Segregation & Liskov Substitution Principles)*
- **`BankClient`**: Interface abstracting external bank communication, allowing the service to remain agnostic of the actual bank integration. `BankSimulatorClient` implements this for the simulator. *(Open/Closed Principle)*

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
| `action` | Recommended client action. | `none` |

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

## 5. Error Handling Strategy

### Decision: Service Throws, Controller Translates

Following clean architecture principles, error handling responsibility is split by layer:

- **Service Layer**: Detects errors (validation failures, bank exceptions) and throws domain exceptions (`PaymentValidationException`, `BankProcessingException`).
- **Controller Layer**: Catches domain exceptions and translates them into `PostPaymentResponse` with appropriate `GatewayCode`.

**Rationale:**
- **Separation of Concerns**: Service knows business rules, Controller knows HTTP contract.
- **Testability**: Service tests verify exception throwing; Controller tests verify response mapping.
- **Consistency**: All error responses share the same JSON structure via `PostPaymentResponse.rejected()` / `bankErrorResponse()`.

---

## 6. Non-Functional Concerns (AOP)

### Decision: Cross-Cutting Concerns via Spring AOP
Rate limiting and metrics are implemented as aspects to avoid polluting business logic:

- **`RateLimitAspect`**: Multi-granularity fixed window (1-second + 60-second) per merchant. Uses `ConcurrentHashMap` with `AtomicInteger` — **O(merchants)** memory.
- **`PaymentMetricsAspect`**: Records payment latency and outcomes via SLF4J. Swappable for Micrometer/Prometheus later.

**Why AOP:** Follows SRP/OCP. Business code does not know about rate limiting or metrics.

---

## 7. Storage and Performance

### Decision: Dual-Indexed In-Memory Repository
The `InMemoryIdempotencyRepository` maintains two internal hash maps (System ID and Idempotency Key). This provides **O(1)** retrieval performance for all `GET` operations while keeping the architecture simple for the current scope.

---

## 8. CI/CD and Quality Gates

### Decision: GitHub Actions with Coverage Enforcement

The project uses GitHub Actions for continuous integration with the following quality gates:

- **Build & Unit Tests**: Compile and run JUnit tests on every PR.
- **Coverage Report**: JaCoCo generates coverage reports, posted as PR comments via `madrapps/jacoco-report`.
- **Coverage Threshold**: 70% minimum for overall and changed files. PRs failing this threshold are blocked from merge.
- **Dependency Review**: Scans PRs for known vulnerabilities in dependencies.
- **PR Size Labeling**: Auto-labels PRs by size (xs/s/m/l/xl) to encourage small, reviewable changes.

**Excluded from Coverage:**
- Swagger API interfaces (`PaymentGatewayApi`)
- Model/POJO classes (`model/**`, `enums/**`)
- Exception classes (`exception/**`)
- Spring configuration (`configuration/**`)
- Unused TODO interfaces (`Metrics`, `RateLimiter`)
- Security wrappers and main class

---

## 9. Assumptions and Limitations

1.  **Volatility**: Data is lost on application restart (In-memory storage).
2.  **Context Trust**: `Merchant-Id` is trusted from the header without cryptographic verification (Exercise scope).
3.  **Synchronous Flow**: Requests wait for the bank's response. Future versions would implement async webhooks.

---

## 10. Design Trade-offs Summary

| Trade-off | Choice | Rationale |
| :--- | :--- | :--- |
| **Status Codes** | 200-Only | Predictable client parsing and business outcome focus. |
| **Response Model** | Inheritance | `GetPaymentResponse` extends `PostPaymentResponse` for semantic clarity without code duplication. |
| **Versioning** | Path (/v1) | Safe evolution and backward compatibility. |
| **Logging** | Masking | PII protection implemented via `toString()` overrides in models. |
| **Rate Limiting** | Multi-granularity Fixed Window | 1s (10 req) + 60s (600 req). Protects downstream while allowing bursts. |
| **Metrics** | AOP + SLF4J | Non-invasive. Upgrade to Micrometer without touching business code. |
| **Swagger** | Interface Separation | `PaymentGatewayApi` holds all OpenAPI annotations; `PaymentGatewayController` stays clean. |
| **Error Handling** | Service Throws / Controller Catches | Clean separation between business logic and HTTP representation. |
| **Coverage** | JaCoCo + PR Enforcement | 70% threshold with automatic PR comments; non-business code excluded. |
