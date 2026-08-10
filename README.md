# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Running the application

### Run the application locally

Make sure Docker is running, then from the project root:

```shell
./gradlew bootRun
```

That is the only command needed. Spring Boot's Docker Compose support reads `docker-compose.yml`,
starts PostgreSQL and the bank simulator, waits until they report healthy, and contributes the
PostgreSQL connection details before the application context refreshes.

The application is then available at `http://localhost:8090`, and Swagger UI at
`http://localhost:8090/swagger-ui/index.html`.

The backing services are stopped again when the application exits.

### Docker Compose

The gateway can also run as a container alongside its backing services, using the `gateway` profile:

```shell
docker compose --profile gateway up --build
```

Wait until the `payment_gateway` service has started. Run Compose in the background by adding `-d`:

```shell
docker compose --profile gateway up --build -d
docker compose logs -f payment_gateway
```

Stop the services while preserving PostgreSQL data:

```shell
docker compose down
```

To stop the services and remove the local database volume:

```shell
docker compose down --volumes
```

### Run the tests

Tests use an in-memory H2 database and do not require Docker services:

```shell
./gradlew test
```

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

## Idempotent payment creation

`POST /payments` requires an `Idempotency-Key` header (1–255 characters).

### Behavior

- The first request for a key processes the payment and returns HTTP `201 Created`.
- Repeating the same key with the same request returns the original payment response without
  calling the bank again.
- Reusing the key with a different request returns HTTP `409 Conflict`.
- Missing, blank, or oversized keys return HTTP `400 Bad Request`.

### Persistence and concurrency

Each idempotency key has its own durable row in PostgreSQL. The row contains the key, a SHA-256
request fingerprint, the creation time, and the resulting payment reference. Card and CVV values
are not stored directly in the idempotency record.

The first request creates a pending row. Requests using that key then acquire a pessimistic lock
on that exact row before checking the fingerprint or calling the bank. Consequently:

- concurrent requests with the same key are serialized;
- one request executes the payment while matching requests replay its committed result;
- requests with different keys use different rows and do not block one another;
- coordination works across application restarts and multiple application instances sharing the
  same PostgreSQL database.

The payment and its association with the idempotency record are committed in the same transaction.
If processing fails before that commit, the pending record remains available for a later retry.

> A database transaction cannot atomically commit an external HTTP operation. To prevent a second
> bank operation after a crash occurring between the bank response and the database commit, the
> acquiring bank must also support the same idempotency key.

Example:

```shell
curl -X POST http://localhost:8090/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: merchant-order-123' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2099,"currency":"USD","amount":100,"cvv":"123"}'
```

**Feel free to change the structure of the solution, use a different library etc.**

## Interview Documentation

### Report 

File that will describe steps done to complete the project
File directory: [interview_report.md](docs/implementation_plan.md)

### Architecture

Documentation that will describe the architecture and possible steps related to it.

File directory: [interview_report.md](docs/architecture.md)


## Final Reflection

### Open questions

1. There is a conflict into the definition of the requirements,  response from https://github.com/cko-recruitment/#processing-a-payment says possible status are `Authorized`, `Declined` but requirements https://github.com/cko-recruitment/#requirements and intial implementation states that there is an extra status. I will assume Rejected is an actual status and clarify it on the interview. Normal delivery flow I would have raised this question before implementing.

### Assumptions

1. Json format require "_" between words in JSON exposed variables, I assumed that was the format requirement, generally I would use camel case.
2. Rejected status was required and it would return the request values.
3. Authentication was not done, but it would be a critical requirement to have some kind of validation related to it
4. Amount format follows the same pattern in both client and bank simulator as there is no specification about it

### What would I do next in a production environment?

1. Time reference needs to be centralized, in a distributed system time reference can change which can impact payments received from different countries, so time should be based on the location of the request and not internal application time.
2. Transaction management should be more granular, with different states, right now it only process 1 transaction per idempotencyID, we could have more states (like FAILED, PROCESSING, SUCCEEDED ) 
3. Kafka part was not done, but for metrics and data collection it would be good to have it.
4. Performance improvements into client, client used to access simulator was too basic and it could have better traceability and performance improvements like configurable pool and recovery mechanism
5. Test coverage could be better checked, overall I always recommend 100% coverage to be sure we have all the scenarios covered
6. Performance tests are missing, I think that would be very important to understand the behaviour an track overall performance of the application under high load
