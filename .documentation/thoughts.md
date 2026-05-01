# Payment Gateway — Design Considerations & Assumptions

## Design Considerations

Took liberty to refactor the whole system, aimed on DDD approach to organize files and package structure.
Also aligned some of the endpoints between GET/POST in sense of responses taking approach of camel case for the attributes since is "our domain", the bank importer kept as is and changed my request model to adapt to it.
Project relies on JDK 25, and Spring boot 4.X, Jackson 3.X.

Dropped the swagger link, as it relies on "Contract first approach". (people might like it, some of them not)

Also relied on Mapstruct/Lombok/OpenAPI generator to reduce some "boilerplace":
- Mapstruct -> basically handle the mappers for you - Unit test was mainly to cover happy cases as the code is generated
- Lombok -> reduce some boiler plate and rely on builder which polutes a lot
- OpenAPI generator -> generates the controller interfaces + dtos (requests/responses)

Added github actions for testing.

## API Design

### Versioning
Relying on the path for it.

### Contract-First Approach
The API was designed using a contract-first approach with an OpenAPI 3.0 specification
(`payment-gateway.yaml`) as the single source of truth. Code is generated from the spec
using the OpenAPI Generator Gradle plugin, ensuring the implementation always aligns
with the contract.

Once the application is started, the file can be used for testing as well :)

### HTTP Status Codes
| Scenario | Status Code | Reason |
|---|---|---|
| Payment Authorized or Declined | 201 Created | New payment resource created |
| Idempotent request (existing payment) | 200 OK | No new resource created |
| Validation failure (Rejected) | 400 Bad Request | Invalid input, bank never called |
| Payment not found | 404 Not Found | Resource does not exist |
| Bank simulator unavailable | 502 Bad Gateway | Upstream dependency failed |
| Unexpected error | 500 Internal Server Error | Unhandled exception |

### Supported Currencies
The application was created to support currencies and provide flexibility in case of new currencies need to be added in the future.

---

## Validation Strategy

### Validation
- **Controller (Bean Validation):** Structural validation via Jakarta Bean
  Validation annotations (`@NotNull`, `@Pattern`, `@Min`, `@Max`). Invalid requests
  are rejected immediately without calling the bank.
- Custom Validation was created for expiry date, might be odd but the validation was implemented in a way to ensure other annotations like `@NotNull` and `@Min/@Max` are respsected and not overlapped by `@ValidExpireDate`. (honestly didn't like, but maintain the responsibility of it)

### Card Number Persistence
The full card number is never stored or returned. Only the last four digits are
persisted and returned in responses, in line with PCI-DSS compliance requirements.
The CVV is never stored under any circumstances.

### Expiry Validation
The combination of expiry month and year is validated to ensure it represents a
future date — not just the year alone. For example, if the current date is May 2026,
a card with expiry 01/2026 is rejected even though 2026 is the current year.

---

## Idempotency

Idempotency is handled at the **controller layer**, my idea was to keep it separate
from the payment processing business logic. This treats idempotency as an
"infrastructure" concern rather than a domain concern, allowing `PaymentsService` to
remain focused purely on payment processing.

- The `Idempotency-Key` header (UUID) is required on all POST requests
- If a key is seen for the first time, the payment is processed and the result cached
- If a key has been seen before, the cached response is returned immediately without
  calling the bank again
- Storage is in-memory (`ConcurrentHashMap`) — in production this would be replaced
  with a distributed cache (e.g. Redis) with a TTL

P.S production scenarios would move maybe to distributed cache like Redis/Hazelcast, or maybe specific table (which would need to reconsider the "infra" assumption and transactional concerns)

---

## Resiliency

### Resilience4j Circuit Breaker + Retry
The bank simulator client is wrapped with Resilience4j to handle transient failures:

- **Retry:** 3 attempts with no exponential backoff (500ms)
- **Circuit Breaker:** Opens after 50% failure rate over a 10-call sliding window,
  preventing cascading failures when the bank is unavailable

### Error Handling Strategy
| Bank Response | Gateway Behaviour |
|---|---|
| 200 Authorized | Payment stored as Authorized, returned to merchant |
| 200 Declined | Payment stored as Declined, returned to merchant |
| 400 Bad Request | Thrown immediately, no retry (client error) |
| 503 Service Unavailable | Retried up to 3 times, then 502 returned to merchant |
| Connection failure | Retried up to 3 times, then 502 returned to merchant |

---

## Storage

An in-memory repository (`ConcurrentHashMap`) is used for payment storage as
permitted by the requirements:

- `PaymentsRepository` — stores payments by ID for retrieval
- `IdempotencyRepository` — stores payment responses by idempotency key

---

## Observability

### Correlation ID
Every request is assigned a correlation ID (`X-Correlation-ID` header), if doesn't exist one will be created.

Enabled end to end tracing across the gateway and the bank.

### Health Indicator
Spring boot actuator, for health endpoint `/health`.

---

## Testing Strategy

### Unit Tests (`*Test`)
- Controller layer (`@WebMvcTest`): all validation scenarios, HTTP status codes,
  idempotency routing, error handling
- Service layer: payment processing logic, mapping, business validation
- Repository layer: in-memory storage behaviour

Some of the tests I prefer to take the approach of making more explicit the assertions and given object even some of most commons is part of TestUtils, my flavour.

### Integration Tests (`*IT`)
- `MounteBankClientIT`: full client tests against the real bank simulator
  (Testcontainers), including resiliency scenarios (retry count, circuit breaker)
- `PaymentsControllerIT`: end-to-end happy path (Authorized, Declined), idempotency
  across real requests, and retrieval of persisted payments


About MounteBankClientIT, ideal scenario I would rely on MockServer or Wikemock to emulate the flows and execution, but since this is docker took the approach of just rely on it for covering the "real scenario".

---

## Assumptions

1. **Rejected payments are not persisted.** If a payment fails gateway validation,
   no record is created. The bank is never called and nothing is stored.
2. **Declined payments are persisted.** A Declined response from the bank is a valid
   payment outcome and is stored for merchant reconciliation.
3. **Currency Flexibility Support** is limited to 4 codes (GBP, USD, EUR, BRL) for now but can be expanded as we wish based on API spec and configuration.
4. **The bank simulator** is being integrated via testcontainer and docker image for better e2e verification emulating "real life" scenarios.
5. **Thread safety** of in-memory repositories is ensured via `ConcurrentHashMap`.

## Improvements
- Would add jacoco for code coverage
- Real scenario I would break this into multiple commits, didn't do as it was a quick POC and public to everyone.
- Revisit card number validation to support more formats and international standards.
- Revisit expire date validation maybe to override the `NotNull/Min/Max` annotations as it can be confusing for review perspective
- Docker and make it more production