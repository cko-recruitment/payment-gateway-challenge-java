# Local Run Guide

One-command local hosting for the Payment Gateway and Bank Simulator.

---

## Prerequisites

- Docker & Docker Compose
- (Optional) `curl` or Postman for API testing

---

## Quick Start

```bash
# Clone and enter project
git clone <repo-url>
cd payment-gateway-challenge-java

# Start everything
docker-compose up --build
```

Services will be available at:

| Service | URL |
|---------|-----|
| Payment Gateway API | http://localhost:8090 |
| Swagger UI | http://localhost:8090/swagger-ui.html |
| Bank Simulator | http://localhost:8080 |
| Mountebank Admin | http://localhost:2525 |

---

## API Test

### Process a Payment

```bash
curl -X POST http://localhost:8090/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-1" \
  -H "Idempotency-Key: key-1" \
  -d '{
    "cardNumber": "4111111111111111",
    "expiryMonth": 12,
    "expiryYear": 2027,
    "currency": "USD",
    "amount": 100,
    "cvv": "123"
  }'
```

**Expected response (authorized — odd last digit):**
```json
{
  "code": "SUCCESS",
  "description": "Payment authorized successfully",
  "action": "none",
  "id": "...",
  "status": "Authorized",
  "cardNumberLastFour": 1111,
  "expiryMonth": 12,
  "expiryYear": 2027,
  "currency": "USD",
  "amount": 100,
  "links": [...]
}
```

### Retrieve by ID

```bash
curl http://localhost:8090/api/v1/payments/{id} \
  -H "Merchant-Id: merchant-1"
```

### Retrieve by Idempotency Key

```bash
curl http://localhost:8090/api/v1/payments/idempotency/key-1 \
  -H "Merchant-Id: merchant-1"
```

---

## Bank Simulator Behaviors

| Card Ending | Result |
|-------------|--------|
| **Odd** (1,3,5,7,9) | `authorized: true` |
| **Even** (2,4,6,8) | `authorized: false` |
| **0** | `503 Service Unavailable` |

---

## Stop

```bash
# Graceful stop
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Port 8090 in use | `docker-compose up -d` then check `docker ps` |
| Build cache issue | `docker-compose build --no-cache` |
| Bank not responding | Verify `bank_simulator` is healthy: `curl http://localhost:2525/imposters` |
