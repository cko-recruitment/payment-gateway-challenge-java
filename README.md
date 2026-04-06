# Payment Gateway

A Spring Boot service that processes card payments through a bank simulator and stores the results. Built with idempotency in mind — duplicate requests with the same key won't charge a card twice.

## Requirements

- JDK 17
- Docker

## Getting started

Start the bank simulator:

```bash
docker-compose up -d
```

Run the application:

```bash
./gradlew bootRun
```

The service starts on port `8090`. Swagger UI is available at:
`http://localhost:8090/swagger-ui/index.html`

## Running tests

```bash
./gradlew test
```

## How it works

- `POST /payment/processPayment` — submits a payment to the bank. Requires an `Idempotency-Key` UUID header.
- `GET /payment/{id}` — retrieves a previously processed payment by its ID.

Payments can come back as `Authorized`, `Declined`, or `Rejected` depending on the bank response.

Idempotency is handled at two levels: completed payments are stored in the repository and returned directly on repeat requests, and in-flight requests with the same key share a single bank call.

## Project structure

```
src/main/java       - Application source code
src/test/java       - Unit and integration tests
imposters/          - Bank simulator config (Mountebank)
docker-compose.yml  - Starts the bank simulator
```