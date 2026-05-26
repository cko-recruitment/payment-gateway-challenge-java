# Load Testing

## Prerequisites

- Docker & Docker Compose
- k6 (via Docker)

## Quick Start

### Smoke Test (1 user, 10 seconds)

```bash
docker run --rm -v $(pwd)/load-tests:/tests \
  -e BASE_URL=http://host.docker.internal:8090 \
  grafana/k6 run /tests/smoke-test.js
```

### Load Test (ramp to 100 VUs)

```bash
# Start app and monitoring first
docker-compose up -d
docker-compose -f docker-compose.observability.yml up -d

# Run load test
docker-compose -f docker-compose.loadtest.yml up
```

## Test Scenarios

| File | VUs | Duration | Purpose |
|------|-----|----------|---------|
| `smoke-test.js` | 1 | 10s | Sanity check |
| `load-test.js` | 10→50→100 | 2.5m | Performance baseline |

## Metrics

k6 outputs:
- `http_req_duration` — Request latency
- `http_reqs` — Requests per second
- `errors` — Error rate

Prometheus/Grafana shows:
- `payment_latency_seconds` — Business latency
- `bank_latency_seconds` — Bank API latency
- `payment_total` — Throughput by status

## Thresholds

| Metric | Threshold |
|--------|-----------|
| P95 latency | < 500ms |
| Error rate | < 10% |

## Stop

```bash
docker-compose -f docker-compose.loadtest.yml down
docker-compose -f docker-compose.observability.yml down
docker-compose down
```
