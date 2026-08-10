# Payment Gateway — Implementation Journey

## Introduction

This report describes the process I followed to complete the payment gateway take-home
exercise. It presents the work chronologically, from understanding the requirements and
defining the architecture to implementing, testing, and reviewing the final solution.

The purpose is to explain not only what I built, but also how the solution evolved and
why I made each decision along the way.

Reference: [Checkout.com Engineering Assessment](https://github.com/cko-recruitment/)

## Step 1 — Understand the Requirements

I started by reading the assessment and translating the requirements into a small set of
observable behaviors.

The gateway needed to:

- accept and validate a payment request;
- send valid payment details to the acquiring-bank simulator;
- return an `Authorized`, `Declined`, or `Rejected` result;
- store the result of a processed payment; and
- retrieve a previous payment without exposing its full card number.

At this stage, I also identified the main constraints: the solution should remain simple,
compile successfully, include automated tests, and focus on the requested functionality
without unnecessary production infrastructure.

### Outcome

I had a clear list of use cases, validation rules, external interactions, and expected
responses to guide the implementation.

## Step 2 — Establish a Basic Solution and Define the Architecture

Before implementing the complete behavior, I reviewed the supplied Spring Boot skeleton
and established a basic layered structure:

- the **controller** owns the HTTP contract;
- the **service** coordinates the payment workflow;
- the **repository** stores and retrieves processed payments into Postgres;
- the **bank client** communicates with the supplied simulator; and
- the **exception handler** converts known failures into consistent API responses.

I chose this structure because it keeps HTTP concerns, business flow, storage, and
external communication separate while remaining small enough for the scope of the
exercise.

The architectural decisions and component relationships are described in more detail in
[Architecture](architecture.md).

### Outcome

I had a minimal foundation on which each part of the payment flow could be implemented
and tested independently.

## Step 3 — Define the API Contract

With the main responsibilities separated, I defined the API operations and their request
and response models.

The operations are:

- `POST /payments` to process a new payment; and
- `GET /payments/{id}` to retrieve an existing payment.

I used UUIDs for payment identifiers and designed the responses to expose only the last
four card digits. I also considered which HTTP status codes should represent invalid
input, missing payments, and downstream failures.

### Outcome

The external behavior of the application was defined before the complete internal flow
was implemented.

## Step 4 — Implement Payment Retrieval First

I implemented the simpler retrieval path first. This connected the controller, service,
and repository and provided an initial end-to-end vertical slice of the application.

At this point the repository used in-memory storage, which the assessment explicitly
allows; it was replaced with PostgreSQL in Step 7. The service retrieves a payment by
UUID and reports a known error when the identifier is not present. The shared exception
handler then maps that error to an HTTP response.

### Outcome

The application could retrieve a stored payment and return a not-found response for an
unknown identifier. This also verified that the initial layering worked as expected.

## Step 5 — Implement Payment Processing

The next step was to implement the main payment-processing workflow:

1. receive the merchant's request;
2. validate the card, expiry date, currency, amount, and CVV;
3. reject invalid input before contacting the bank;
4. send valid requests to the bank simulator;
5. translate the bank response into the gateway's payment status; and
6. store and return the processed payment.

`PaymentGatewayService.processPayment` runs these stages in order. Validation happens
first, so an invalid request never reaches the simulator. A valid request is forwarded by
`BankSimulatorService`, and the bank's decision is mapped onto the stored payment:
`authorized: true` becomes `AUTHORIZED` and carries the bank's authorization code, while
anything else becomes `DECLINED`.

### Outcome

`POST /payments` processes a payment end to end and returns `201 Created` with the
resulting status. The payment is persisted with its card number and CVV encrypted, and
the response exposes only the last four digits of the card. `GET /payments/{id}` returns
the same view of a previously processed payment.

## Step 6 — Add Validation and Failure Handling

After establishing the happy path, I addressed invalid requests and failure scenarios.
The important distinction was between:

- **Rejected:** the gateway refused invalid merchant input without calling the bank;
- **Declined:** the bank processed the request but did not authorize the payment; and
- **Downstream failure:** the bank could not provide a valid decision.

I kept these outcomes separate so that API consumers could understand whether they
should correct the request, accept the bank's decision, or retry later.

Validation is applied in two complementary layers. Jakarta Bean Validation annotations on
`PaymentRequest` cover field-level format rules — card number of 14–19 digits, CVV of 3–4
digits, a three-letter uppercase currency code, and a positive amount. Rules that depend
on configuration or on more than one field live behind a `ValidationRule` interface, so
each is a separate Spring bean that `PaymentValidator` collects and applies in turn:

- `CardExpiryRule` — the expiry month and year must be in the future;
- `CurrencyRule` — the currency must be one of the configured
  `payment.supported-currencies` (`USD`, `EUR`, `GBP`); and
- `IdempotencyKeyRule` — the `Idempotency-Key` header must be 1–255 characters.

I chose this structure so that a new business rule is a new class rather than another
branch inside an existing method.

### Outcome

Both validation layers converge on `CommonExceptionHandler`, which returns
`400 Bad Request` with a `Rejected` status, a combined `error_message`, and a per-field
`errors` list using snake_case field names. Failures map to distinct responses:

| Condition | Response |
| --- | --- |
| Field or business rule violation | `400 Bad Request`, status `Rejected` |
| Malformed request body | `400 Bad Request`, status `Rejected` |
| Key reused with a different request | `409 Conflict` |
| Unknown payment identifier | `404 Not Found` |
| Bank unreachable, or empty/invalid decision | `502 Bad Gateway` |

`BankSimulatorService` treats a transport error, a missing response, and a null
`authorized` field alike: each raises `BankIntegrationException`, which
`BankIntegrationExceptionHandler` maps to `502`. This keeps an ambiguous downstream
result from ever being recorded as a decision.

## Step 7 — Add Persistence and Idempotency

With the flow correct, I replaced in-memory storage with PostgreSQL through Spring Data
JPA, so that processed payments and their idempotency state survive a restart and can be
shared by more than one instance.

`POST /payments` requires an `Idempotency-Key` header. Each key owns a durable row in
`idempotency_records` holding a SHA-256 fingerprint of the request, the creation time,
and a reference to the resulting payment. The first request for a key inserts a pending
row; every request for that key then takes a pessimistic lock on that row before
comparing fingerprints or calling the bank.

### Outcome

Repeating a request with the same key and the same body replays the original response
without contacting the bank again, while reusing a key with a different body returns
`409 Conflict`. Because the lock is held on a database row, concurrent requests with the
same key are serialized, requests with different keys do not block one another, and the
behavior holds across restarts and multiple instances. The payment and its link to the
idempotency record are committed in one transaction, so a failure before that commit
leaves the pending row available for a retry.

## Step 8 — Build the Automated Test Suite

I added tests incrementally as each behavior became available. The first controller
tests covered successful retrieval and retrieval of an unknown payment.

The test suite demonstrates:

- retrieval of an existing payment;
- retrieval of an unknown payment;
- authorized and declined payment processing;
- rejection of each invalid input category;
- the fact that rejected input never reaches the bank;
- handling of bank errors and timeouts;
- idempotent replay and idempotency-key conflicts; and
- masking of sensitive card data.

Tests run against an in-memory H2 database configured in PostgreSQL compatibility mode,
so `./gradlew test` needs no Docker services.

### Outcome

39 tests across 15 classes, covering the controller, service, validation rules, exception
handlers, bank client, and persistence. JaCoCo reports 98% instruction coverage. The
tests provide executable evidence of the required behavior and protect the main decisions
made during implementation.

## Step 9 — Review Security-Sensitive Behavior

Because this project handles card information, I reviewed how sensitive values move
through the application. In particular, I checked that full card numbers and CVVs were
not returned, persisted in the clear, or written to logs.

### Outcome

Card numbers and CVVs are encrypted before persistence using Spring Security's
`Encryptors.delux` (AES-GCM), with the secret and salt supplied through the
`PAYMENT_ENCRYPTION_SECRET` and `PAYMENT_ENCRYPTION_SALT` environment variables rather
than hard-coded. Responses expose only the last four digits of the card, and the
idempotency record stores a SHA-256 fingerprint of the request instead of the card data
itself. Log statements record identifiers and validation messages, never card numbers or
CVVs.

For the scope of this exercise, this level of protection is proportionate. A production
system would add tokenization, managed key rotation, access controls, and PCI DSS
processes.

## Step 10 — Document and Demonstrate the Final Solution

I performed a final review against the original requirements and prepared the following
demonstration:

1. start the gateway with `./gradlew bootRun`, which also starts the PostgreSQL and bank
   simulator containers;
2. run the automated tests with `./gradlew test`;
3. inspect the API through Swagger UI at
   [http://localhost:8090/swagger-ui/index.html](http://localhost:8090/swagger-ui/index.html);
4. process authorized, declined, and rejected payment examples;
5. repeat a request with the same `Idempotency-Key` to show the replayed response; and
6. retrieve a processed payment by its identifier.

### Outcome

The final solution meets the functional requirements of the assessment. `./gradlew
bootRun` starts the application and its dependencies with a single command, `./gradlew
test` passes with 39 tests and 98% instruction coverage, and the remaining improvements I
would prioritize in a production environment are listed in the project
[README](../README.md).