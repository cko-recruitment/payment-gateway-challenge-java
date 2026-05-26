# Load Testing Guide

## Tools

We use **k6** for load testing. Alternatives include JMeter, Gatling, Locust, Apache Bench (ab), and wrk.

| Tool | Language | Best For |
|------|----------|----------|
| **k6** | JavaScript | Cloud-native, containerized, Grafana integration |
| JMeter | Java/GUI | Complex scenarios, GUI configuration |
| Gatling | Scala/Kotlin | High performance, CI/CD integration |
| Locust | Python | Distributed testing, Python teams |
| ab | C | Quick single-endpoint validation |
| wrk | C | Extreme QPS testing |

## Prerequisites

- Docker & Docker Compose
- Application running (`docker-compose up -d`)
- Observability stack optional (`docker-compose -f docker-compose.observability.yml up -d`)

## k6 Built-in Variables

| Variable | Type | Description |
|----------|------|-------------|
| `__VU` | number | Virtual User ID (starts at 1) |
| `__ITER` | number | Iteration count per VU (starts at 0) |
| `__ENV` | object | Environment variables access |

### Usage Example

```javascript
const merchantId = `merchant-${__VU}`;
const idempotencyKey = `key-${__VU}-${__ITER}`;
```

- `__VU` ensures each virtual user uses a different merchant
- `__ITER` ensures idempotency keys are unique across iterations

## Quick Start

### Smoke Test (1 user, 10 seconds)

```bash
docker run --rm \
  -v $(pwd)/load-tests:/tests \
  -e BASE_URL=http://host.docker.internal:8090 \
  grafana/k6 run /tests/smoke-test.js
```

### Load Test (ramp to 100 VUs)

```bash
# Start observability stack (optional but recommended)
docker-compose -f docker-compose.observability.yml up -d

# Run load test
docker-compose -f docker-compose.loadtest.yml up
```

## Test Scenarios

| File | VUs | Duration | Purpose |
|------|-----|----------|---------|
| `smoke-test.js` | 1 | 10s | Sanity check |
| `load-test.js` | 10→50→100 | 2.5m | Performance baseline |

## k6 Script Reference

### HTTP Requests

```javascript
import http from 'k6/http';

http.get(url, params);
http.post(url, body, params);
http.put(url, body, params);
http.del(url, body, params);
```

### Checks (Assertions)

```javascript
import { check } from 'k6';

check(response, {
  'status is 200': (r) => r.status === 200,
  'response time < 500ms': (r) => r.timings.duration < 500,
});
```

### Custom Metrics

```javascript
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

const myCounter = new Counter('my_counter');
const myTrend = new Trend('my_trend');

export default function () {
  myCounter.add(1);
  myTrend.add(100);
}
```

### Lifecycle Hooks

```javascript
// Setup: runs once before all VUs
export function setup() {
  const token = login();
  return { token };
}

// Default: runs repeatedly for each VU
export default function (data) {
  http.get(url, { headers: { Authorization: data.token } });
}

// Teardown: runs once after all VUs complete
export function teardown(data) {
  console.log('Test completed');
}
```

### Options Configuration

```javascript
export const options = {
  vus: 10,                    // Virtual users
  duration: '30s',            // Test duration
  iterations: 100,            // Total iterations
  stages: [                   // Ramp stages
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 100 },
  ],
  thresholds: {               // Pass/fail criteria
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.1'],
  },
};
```

## Thresholds

| Metric | Threshold | Meaning |
|--------|-----------|---------|
| P95 latency | < 500ms | 95% of requests under 500ms |
| Error rate | < 10% | Less than 10% failed requests |

## Metrics During Test

### k6 Output
- `http_req_duration` — Request latency
- `http_reqs` — Requests per second
- `errors` — Error rate

### Prometheus/Grafana
- `payment_latency_seconds` — Business-level latency
- `bank_latency_seconds` — Bank API latency
- `payment_total` — Throughput by status

## Grafana Dashboard

Access http://localhost:3000 during load test (admin/admin).

Watch for:
- QPS trends
- Latency percentiles
- Error spikes
- JVM memory/CPU usage

## Cleanup

```bash
# Stop load test
docker-compose -f docker-compose.loadtest.yml down

# Stop observability
docker-compose -f docker-compose.observability.yml down

# Stop application
docker-compose down
```
