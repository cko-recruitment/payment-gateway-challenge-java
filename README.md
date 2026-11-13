# Payment gateway

Java 17 / Spring Boot implementation of the Checkout.com payment gateway challenge.
Processes payments synchronously through the supplied acquirer simulator and retrieves
completed payments by ID.

## Run

Requires JDK 17 (`JAVA_HOME`), Docker, and Docker Compose 2.20+.

```sh
bash ./gradlew clean test bootJar
docker compose -f docker-compose.demo.yml up -d --build
```

The demo starts the gateway on **8090** and simulator on **8080**. Allow both services
to finish starting. Swagger UI: http://localhost:8090/swagger-ui/index.html

```sh
# Logs / stop
docker compose -f docker-compose.demo.yml logs -f payment_gateway
docker compose -f docker-compose.demo.yml down
```

Alternatively, run the simulator with `docker compose up -d`, then run the gateway
with `bash ./gradlew bootRun`. `ACQUIRER_URL` defaults to
`http://localhost:8080/payments`; connect and read timeouts each default to 2000 ms.
The demo Compose file configures the container acquirer address automatically.
The supplied `docker-compose.yml`, `imposters/`, and `.editorconfig` are unchanged.

## API

```sh
curl -i http://localhost:8090/payments \
  -H 'Content-Type: application/json' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2099,"currency":"GBP","amount":1050,"cvv":"123"}'
```

A completed authorization or decline returns `201 Created`, a `Location: /payments/{id}`
header, and masked payment details:

```json
{
  "id": "73649161-dc37-4078-955a-13bddc5860cd",
  "status": "Authorized",
  "card_number_last_four": "8877",
  "expiry_month": 12,
  "expiry_year": 2099,
  "currency": "GBP",
  "amount": 1050
}
```

`GET /payments/{id}` returns the same details with `200 OK`. API documentation is also
available at `/v3/api-docs`.

| Condition | HTTP status |
| --- | --- |
| Invalid request or malformed UUID | 400 |
| Payment not found | 404 |
| Invalid acquirer response or unexpected non-5xx status | 502 |
| Acquirer 5xx, connection failure, or timeout | 503 |

All payment fields are required. Card numbers accept 14–19 ASCII digits, CVVs 3–4,
and currency is `INR`, `GBP`, or `EUR`. Amount is a positive 32-bit integer in minor
units. Expiry must be strictly after the current UTC month, interpreting the brief's
“in the future” requirement; the current month is rejected.

For demos, the simulator authorizes odd final card digits, declines nonzero even digits,
and returns 503 for a final zero.

## Design and tradeoffs

The controller handles HTTP and validation; the service coordinates processing; the
acquirer client owns the external contract; and the repository stores immutable,
masked results in a `ConcurrentHashMap`. Full card numbers and CVVs are neither stored
nor logged. The Docker image runs on a Java 17 JRE as a non-root user.

Storage is per process, lost on restart, and has no eviction. Run one gateway replica;
multiple replicas require shared storage. Authentication, durable storage, idempotency,
and reconciliation are outside this implementation. No automatic retries are made:
a timeout can leave the payment outcome unknown, and resubmitting may duplicate a charge.

## Observability

Console logs include a generated `X-Request-ID` (also returned and forwarded to the
acquirer), safe payment outcomes, HTTP status, and timings. Request bodies and card
information are excluded.

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health` | Application health |
| `/actuator/health/liveness` | Process liveness |
| `/actuator/health/readiness` | Application readiness; does not check the acquirer |
| `/actuator/metrics/http.server.requests` | API counts, statuses, and timings |
| `/actuator/metrics/http.client.requests` | Acquirer HTTP counts and timings |
| `/actuator/metrics/payments.processed` | Payment processing counts by outcome |

`payments.processed` uses only `outcome=authorized|declined|acquirer_unavailable|acquirer_error`.
It counts stored outcomes or acquirer failures, excluding validation failures and lookups.
Metrics appear after relevant activity and reset on restart. Health details are hidden;
management endpoints have no application authentication and should remain internal.

## Tests

```sh
bash ./gradlew test
```

Tests are structured bottom-up: validation and repository behavior, acquirer wire
contracts and failures, service orchestration and counters, then REST integration
coverage. They cover authorization, decline, masked retrieval, invalid requests,
missing payments, acquirer errors/timeouts, and request correlation. The acquirer is
simulated in tests; Docker is not required.

Report: `build/reports/tests/test/index.html`.

## Dependency security

Springdoc stays on the Spring Boot 3.1-compatible 2.2 line. Targeted overrides update
Commons Lang to 3.18.0, SnakeYAML to 2.3, Jackson to 2.18.11, and Swagger UI to 5.34.1.
A fixed English locale prevents unbounded locale-keyed OpenAPI caching
([Springdoc advisory](https://github.com/springdoc/springdoc-openapi/security/advisories/GHSA-rhhx-6j8h-8cvw));
this is a mitigation, not a Springdoc version upgrade. Keep documentation endpoints internal
in a hosted deployment. These changes address the documentation dependency findings,
not a complete security audit of the older Spring Boot baseline.
