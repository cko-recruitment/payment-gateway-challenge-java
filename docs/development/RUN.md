# Running the Application

## Quick Start

```bash
# Build and run with Docker Compose
docker-compose up --build

# Or run locally
./gradlew bootRun
```

## Local Development

### Prerequisites
- Java 17+
- Docker & Docker Compose

### Run with Bank Simulator
```bash
# Start bank simulator
docker run -d --name bank-simulator \
  -p 2525:2525 -p 8080:8080 \
  -v $(pwd)/imposters:/imposters \
  bbyars/mountebank:2.8.1 \
  --configfile /imposters/bank_simulator.ejs --allowInjection

# Start application
./gradlew bootRun
```

### API Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/payments` | POST | Process payment |
| `/api/v1/payments/{id}` | GET | Retrieve by ID |
| `/api/v1/payments/idempotency/{key}` | GET | Retrieve by idempotency key |
| `/actuator/health` | GET | Health check |
| `/swagger-ui.html` | GET | API documentation |

## Testing

```bash
# Unit tests
./gradlew test

# Integration tests (requires bank simulator)
./gradlew integrationTest

# Functional tests (requires bank simulator)
./gradlew functionalTest

# All tests
./gradlew test integrationTest functionalTest
```

## Docker Compose Profiles

```bash
# Basic stack
 docker-compose up

# With observability (Prometheus + Grafana)
docker-compose -f docker-compose.yml -f docker-compose.observability.yml up

# With distributed tracing (Jaeger)
docker-compose -f docker-compose.yml -f docker-compose.tracing.yml up

# Load testing stack
docker-compose -f docker-compose.yml -f docker-compose.loadtest.yml up
```

## Troubleshooting

### Bank Simulator Connection Failed
```bash
# Check if bank simulator is running
curl http://localhost:2525/imposters

# Restart bank simulator
docker restart bank-simulator
```

### Port Already in Use
```bash
# Find process using port 8090
lsof -i :8090

# Or change port in application.properties
server.port=8091
```

### Rate Limit Not Triggering
Rate limit is per merchant (10 req/s). If load test uses different merchants for each VU, limit won't trigger.

Fix: Share merchants across VUs in `load-tests/load-test.js`.

### JaCoCo Coverage Missing IT/FT
Integration and functional tests have separate JaCoCo exec files:
- `build/jacoco/integrationTest.exec`
- `build/jacoco/functionalTest.exec`

Run `./gradlew jacocoFullReport` to combine all.
