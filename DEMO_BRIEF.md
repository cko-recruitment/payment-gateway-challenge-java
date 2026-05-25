# Payment Gateway - Live Demo Brief

## Project Overview

A Spring Boot payment gateway API that allows merchants to process card payments and retrieve payment details. The gateway validates requests, forwards valid payments to an acquiring bank simulator, and stores authorized/declined payments for reconciliation.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.1.5 |
| Language | Java 17 |
| Build Tool | Gradle 8.2.1 |
| Testing | JUnit 5, Mockito, AssertJ |
| Documentation | OpenAPI / Swagger UI |
| Bank Simulator | Mountebank (Docker) |

---

## API Endpoints

### 1. Process Payment
```
POST /api/payment
```

**Request:**
```json
{
  "card_number": "4242424242424241",
  "expiry_month": 12,
  "expiry_year": 2026,
  "currency": "USD",
  "amount": 1050,
  "cvv": "123"
}
```

**Responses:**

| Card Number Ending | Status | Bank Called? |
|-------------------|--------|-------------|
| Odd (1,3,5,7,9) | `Authorized` | Yes |
| Even (2,4,6,8) | `Declined` | Yes |
| 0 | Bank Error (503) | Yes, fails |
| Invalid input | `Rejected` | No |

**Authorized Example:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 4241,
  "expiryMonth": 12,
  "expiryYear": 2026,
  "currency": "USD",
  "amount": 1050
}
```

**Rejected Example:**
```json
{
  "status": "Rejected",
  "message": "Card number must only contain numeric characters; CVV must be 3 or 4 characters long"
}
```

---

### 2. Retrieve Payment
```
GET /api/payment/{id}
```

**Success (200):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 4241,
  "expiryMonth": 12,
  "expiryYear": 2026,
  "currency": "USD",
  "amount": 1050
}
```

**Not Found (404):**
```json
{
  "code": "PAYMENT_NOT_FOUND",
  "message": "Payment not found"
}
```

---

## Validation Rules

| Field | Rules |
|-------|-------|
| Card Number | Required, 14-19 digits, numeric only |
| Expiry Month | 1-12 |
| Expiry Year | Must be in the future (month + year) |
| Currency | 3 letters (ISO code format) |
| Amount | Positive integer (minor currency unit) |
| CVV | 3-4 digits, numeric only |

---

## Architecture

```
Merchant          Payment Gateway        Bank Simulator
   |                    |                      |
   |--- POST /payment -->|                     |
   |                    |--- validate -------->|
   |                    |--- forward --------->|
   |                    |<-- response ---------|
   |<-- 200 + result ---|                     |
   |                    |                      |
   |--- GET /payment/{id}                     |
   |                    |--- query store       |
   |<-- 200 or 404 -----|                     |
```

### Key Design Decisions

1. **Rejected payments are NOT stored** — per requirement: "No payment could be created"
2. **Rejected returns 200 OK with status=Rejected** — not an HTTP error, it's a valid business outcome
3. **Full card number never stored or returned** — only last 4 digits in response
4. **In-memory repository** — sufficient for this challenge, no DB needed
5. **Unified exception hierarchy** — `PaymentGatewayException` base class with HTTP status + error code

---

## Test Coverage

| Layer | Count | Files |
|-------|-------|-------|
| Unit Test | 9 | `PaymentGatewayServiceTest`, `PaymentGatewayControllerTest`, `PaymentGatewayControllerWebTest` |
| Integration Test | 2 | `PaymentGatewayIntegrationTest` |
| Functional Test | 8 | `PaymentGatewayFunctionalTest` |
| **Total** | **19** | **All passing** |

### Test Scenarios Covered

- Authorized (odd card number)
- Declined (even card number)
- Rejected (invalid card number, currency, expiry, CVV, multiple errors)
- Retrieve existing payment
- 404 for non-existent payment
- Error response structure (code + message)

---

## Live Demo Commands

```bash
# 1. Start bank simulator
docker-compose up -d

# 2. Run all tests
./gradlew test integrationTest functionalTest

# 3. Start application
./gradlew bootRun

# 4. Open Swagger UI
open http://localhost:8090/swagger-ui/index.html

# 5. Test with curl
curl -X POST http://localhost:8090/api/payment \
  -H "Content-Type: application/json" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2026,"currency":"USD","amount":100,"cvv":"123"}'
```

---

## Swagger UI

Available at: **http://localhost:8090/swagger-ui/index.html**

Interactive API documentation with try-it-now feature.

---

## Interview Q&A

### Q1: Why does Rejected return 200 OK instead of 400 Bad Request?

**A:** The requirement defines three business outcomes: Authorized, Declined, and Rejected. Rejected is a valid business result, not a client error. Returning 200 with `status=Rejected` makes the API consistent — merchants always get a `PostPaymentResponse` and can handle all three statuses uniformly. HTTP 400 would imply the client made a malformed request, which isn't the case here.

### Q2: Why didn't you use a database?

**A:** The requirement explicitly states "You do not need to integrate with a real storage engine or database. It is fine to use the test double repository provided." I used an in-memory `HashMap` backed by a `@Repository` — this keeps the solution simple and maintainable. In production, I'd swap it for a real database with minimal code changes.

### Q3: How would you handle the bank being unavailable?

**A:** Currently, bank errors throw `BankProcessingException` which returns 502 Bad Gateway. In production, I'd add:
- Circuit breaker (Resilience4j) to fail fast when bank is down
- Retry with exponential backoff for transient failures
- Async processing with a queue (e.g., Kafka) for deferred settlement
- Idempotency keys to prevent duplicate charges on retry

### Q4: Why did you create a custom exception hierarchy?

**A:** I wanted a clean separation between business errors and unexpected errors. `PaymentGatewayException` carries both an HTTP status and an error code, so the global exception handler can treat all business exceptions uniformly. This also makes it easy to add new exception types without modifying the handler.

### Q5: How would you extend this for production?

**A:**
- **Security:** HTTPS, API key authentication, PCI DSS compliance (tokenize card numbers)
- **Storage:** PostgreSQL with encrypted card data, Redis for caching
- **Observability:** Structured logging (JSON), distributed tracing (OpenTelemetry), metrics (Micrometer + Prometheus)
- **Scalability:** Stateless services behind a load balancer, async payment processing
- **Idempotency:** Idempotency-Key header to prevent duplicate payments

---

## Demo Script (3 minutes)

1. **Show tests passing** (30s)
   ```bash
   ./gradlew test integrationTest functionalTest
   ```
   Highlight: 19 tests, 3 layers, all green.

2. **Start app + Swagger** (30s)
   ```bash
   ./gradlew bootRun
   ```
   Open Swagger UI, show the two endpoints.

3. **Process a payment** (60s)
   - Odd card → Authorized, copy the ID
   - Even card → Declined
   - Invalid card → Rejected, show the message

4. **Retrieve the payment** (30s)
   - Use the ID from step 3 → 200 with full details
   - Random UUID → 404 with structured error

5. **Walk through code** (30s)
   - Show `PaymentGatewayService.processPayment()` — validation → bank call → store
   - Show exception hierarchy — clean, extensible
   - Show test structure — unit / integration / functional separation
