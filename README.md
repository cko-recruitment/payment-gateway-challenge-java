# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Design considerations and assumptions
- The gateway exposes:
  - `POST /payment` to process a card payment
  - `GET /payment/{id}` to retrieve a previously created payment
- Validation is performed in-gateway before calling the bank simulator:
  - card number must be numeric and 14-19 digits
  - expiry month must be `1..12`
  - expiry month/year combination must be in the future
  - currency is restricted to three ISO codes: `USD`, `EUR`, `GBP`
  - amount must be greater than zero
  - cvv must be 3-4 digits
- Validation failures return `Rejected` and do not call the acquiring bank.
- Valid requests call the bank simulator (`bank.simulator.url`, default `http://localhost:8080/payments`):
  - `authorized=true` => `Authorized`
  - `authorized=false` => `Declined`
  - bank unavailability => `503 Service Unavailable`
- `POST /payment` accepts optional `Idempotency-Key`:
  - same key + same request payload returns the original response
  - same key + different payload returns `409 Conflict`
- Only successful bank-submitted requests (`Authorized`/`Declined`) are stored in the in-memory repository.
- Responses only include the last four card digits.
- Operational observability:
  - actuator endpoints expose `health`, `info`, and `metrics`
  - health includes a `bankSimulator` component that checks bank URL connectivity
  - business metrics include:
    - `payment.requests.total`
    - `payment.requests.authorized`
    - `payment.requests.declined`
    - `payment.requests.rejected`
    - `payment.requests.idempotency_replay`
    - `payment.requests.idempotency_conflict`
    - `payment.requests.bank_unavailable`