# Design Considerations and Assumptions

## Overview

This document outlines the key design decisions and assumptions made while building the Payment Gateway API as part of an offline take-home exercise.

---

## 1. Architecture and Layering

### Decision: Three-Layer Architecture

The application follows a classic three-layer architecture:

```
Controller (HTTP/REST) → Service (Business Logic) → Repository (Data Access)
                              ↓
                        BankClient (External API)
```

**Rationale:**
- Clear separation of concerns makes the codebase maintainable and testable
- Each layer has a single responsibility
- Easy to swap implementations (e.g., in-memory → database, simulator → real bank)

---

## 2. Interface-Based Design

### Decision: Program to Interfaces

Both external dependencies are abstracted behind interfaces:

| Interface | Implementation | Future Replacement |
|-----------|---------------|-------------------|
| `BankClient` | `BankSimulatorClient` | `RealBankClient` |
| `PaymentRepository` | `InMemoryPaymentRepository` | `JpaPaymentRepository` |

**Rationale:**
- Follows the Dependency Inversion Principle (SOLID)
- `PaymentGatewayService` depends on abstractions, not concrete implementations
- Zero code changes in Service layer when swapping implementations
- Enables easy mocking in unit tests

---

## 3. Exception Handling Strategy

### Decision: Controller Owns All HTTP Response Conversion

**Service Layer:**
- Returns data for successful operations
- Throws domain exceptions for business errors (`PaymentValidationException`, `BankProcessingException`)
- Returns `Optional<PostPaymentResponse>` for queries that may not find data

**Controller Layer:**
- Catches all business exceptions and converts to HTTP responses
- Assembles `Rejected` / `Declined` / `404` responses directly
- Handles empty `Optional` with `.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build())`
- Does NOT delegate to `@ControllerAdvice` for expected business errors

**Global Exception Handler (`@ControllerAdvice`):**
- Only handles truly unexpected errors (`Exception.class` → 500 INTERNAL_ERROR)
- Not involved in normal business flow

**Rationale:**
- Service remains pure business logic, unaware of HTTP
- Controller is the single place for HTTP contract decisions
- No split responsibility between Controller and `@ControllerAdvice`
- Easier to understand: all response logic is in one layer

---

## 4. Payment Status Design

### Decision: Three Distinct Business Outcomes

| Status | HTTP Code | Meaning | Payment Stored? |
|--------|-----------|---------|----------------|
| `Authorized` | 200 | Bank approved the payment | Yes |
| `Declined` | 200 | Bank rejected the payment | Yes |
| `Rejected` | 200 | Gateway rejected invalid input | No |

**Key Assumption:** `Rejected` is a valid business outcome, not an HTTP error.

**Rationale:**
- Per requirement: "No payment could be created as invalid information was supplied"
- Returning 400 would imply a malformed HTTP request, which is not the case
- Merchants can handle all three statuses uniformly from the response body
- `Rejected` payments have `id = null` and are not stored

---

## 5. Bank Error Handling (503)

### Decision: 503 → Declined

When the bank simulator returns `503 Service Unavailable` (card ending in 0):

```
Bank 503 → Controller catches → Returns Declined with message
```

**Rationale:**
- The bank is temporarily unavailable, not the gateway
- From the merchant's perspective, the payment could not be completed
- `Declined` is the appropriate business status (retryable in production)
- Distinguishes from input validation errors (`Rejected`)

---

## 6. Data Storage

### Decision: In-Memory HashMap

**Assumption:** The requirement explicitly states: "You do not need to integrate with a real storage engine or database. It is fine to use the test double repository provided."

**Rationale:**
- Sufficient for the scope of this exercise
- `PaymentRepository` interface allows seamless replacement with a real database
- No external dependencies (no Docker setup for DB)
- Thread safety was not a requirement; `HashMap` is sufficient for single-instance testing

---

## 7. Validation Strategy

### Decision: Collect All Errors

The validator collects all validation errors into a list and returns them together:

```java
List<String> errors = validateRequest(request);
if (!errors.isEmpty()) {
    throw new PaymentValidationException(String.join("; ", errors));
}
```

**Rationale:**
- Better user experience: merchant sees all problems at once
- Reduces round-trips for fixing multiple input errors
- Error messages are joined with `; ` for readability

---

## 8. Security Considerations

### Decision: Comprehensive PCI DSS-Aware Logging

**1. SecureLogger with Strategy Pattern**

A dedicated `SecureLogger` component ensures sensitive data is masked before any log output:

```
security/
├── MaskingStrategy.java              # Interface
├── CardNumberMaskingStrategy.java    # **** + last 4 digits
├── CvvMaskingStrategy.java           # * repeated
└── SecureLogger.java                 # Entry point
```

**2. POJO toString() Masking**

All model classes override `toString()` to mask sensitive fields:

| Field | toString() Output |
|-------|-------------------|
| Card Number | `****4242` |
| CVV | `***` |

Example:
```
PostPaymentRequest{cardNumber='****4242', expiryMonth=12, expiryYear=2026, currency='USD', amount=100, cvv='***'}
```

**3. No Raw SLF4J in Business Code**

All Controllers, Services, and Clients use `SecureLogger` instead of direct `LoggerFactory`. This prevents accidental unmasked logging.

**4. Data Lifecycle**

| Stage | Card Number | CVV | Last 4 Digits |
|-------|-------------|-----|---------------|
| Request (JSON) | Full (HTTPS) | Full (HTTPS) | N/A |
| Log Output | Masked | Masked | N/A |
| Storage | Not stored | Not stored | Stored |
| Response | Not returned | Not returned | Returned |

**Assumption:** PCI DSS compliance is partially implemented. Full compliance would require tokenization, encryption at rest, network segmentation, and regular security audits.

---

## 9. Testing Strategy

### Decision: Three Test Layers

| Layer | Scope | Technology |
|-------|-------|-----------|
| Unit Test | Service logic, Controller behavior | JUnit 5, Mockito |
| Integration Test | BankClient HTTP interaction | Spring Boot Test |
| Functional Test | End-to-end API flows | Spring Boot Test, TestRestTemplate |

**Rationale:**
- Unit tests are fast and isolate failures
- Integration tests verify real HTTP contracts with the bank simulator
- Functional tests validate complete business scenarios
- Total: 24 tests, all automated and passing

---

## 10. Assumptions and Limitations

### Known Limitations (Acceptable for Exercise)

1. **Controller Catch-All:** The Controller has a large `try-catch` block for `processPayment`. In a larger application, this could be extracted to a dedicated mapper or moved to `@ControllerAdvice` with custom response types. For this exercise, keeping it explicit in the Controller makes the flow clear.

2. **No Idempotency:** Calling `POST /payment` twice with the same data creates two payments. In production, an `Idempotency-Key` header would be required.

3. **No Async Processing:** Bank calls are synchronous. Production would use async processing (message queues) for resilience.

4. **No Retry Logic:** Bank failures are immediately returned. Production would implement exponential backoff with circuit breakers.

5. **Single Instance:** In-memory storage only works with a single application instance. Production would use a shared database.

6. **No Authentication:** The API is open. Production would require API keys or OAuth2.

7. **Currency Whitelist:** Only validates format (3 letters), not actual ISO currency codes. Requirement says "no more than 3 currency codes" but does not specify which ones.

### Production Readiness Checklist

- [ ] Replace `InMemoryPaymentRepository` with PostgreSQL + JPA
- [ ] Add idempotency key support
- [ ] Implement async payment processing (Kafka/SQS)
- [ ] Add circuit breaker (Resilience4j) for bank calls
- [ ] Add API authentication (API keys or OAuth2)
- [ ] Add structured logging and distributed tracing
- [ ] Add metrics and monitoring (Micrometer + Prometheus)
- [ ] Encrypt sensitive data at rest
- [ ] Implement webhook notifications for async status updates

---

## 11. Key Design Trade-offs

| Trade-off | Choice | Alternative | Why |
|-----------|--------|-------------|-----|
| Rejected → 200 vs 400 | 200 | 400 Bad Request | Rejected is a business outcome, not HTTP error |
| Interface vs Concrete | Interface | Direct dependency | Enables swapping implementations |
| Service throws vs returns | Throws | Returns wrapper object | Cleaner separation, easier testing |
| Controller vs @ControllerAdvice | Controller handles all | Split between both | Single place for HTTP logic |
| Collect all vs fail fast | Collect all | Return first error | Better UX for API consumers |
| Sync vs Async bank call | Sync | Async with callback | Simpler for exercise scope |
