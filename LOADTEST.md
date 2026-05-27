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
- Observability stack (`docker-compose -f docker-compose.observability.yml up -d`)

## Architecture

```
┌─────────┐     HTTP      ┌─────────────────┐
│   k6    │ ─────────────►│  Payment Gateway│
│ (Load)  │               │   :8090         │
└─────────┘               └────────┬────────┘
                                   │
                          ┌────────▼────────┐
                          │  Bank Simulator │
                          │   :8080         │
                          └─────────────────┘
                                   │
                          ┌────────▼────────┐
                          │   Prometheus    │◄── scrape /actuator/prometheus
                          │   :9090         │
                          └────────┬────────┘
                                   │ query
                          ┌────────▼────────┐
                          │    Grafana      │◄── Dashboard http://localhost:3000
                          │   :3000         │
                          └─────────────────┘
```

## Step-by-Step Operation

### 1. Start Application Stack

```bash
# Terminal 1: Start payment gateway + bank simulator
docker-compose up -d

# Verify
docker-compose ps
curl http://localhost:8090/actuator/health
```

### 2. Start Observability Stack

```bash
# Terminal 2: Start Prometheus + Grafana
docker-compose -f docker-compose.observability.yml up -d

# Verify Prometheus targets
curl http://localhost:9090/api/v1/targets

# Open Grafana (admin/admin)
open http://localhost:3000/d/payment-gateway/payment-gateway
```

### 3. Run Smoke Test

```bash
# Terminal 3: Quick sanity check
docker run --rm \
  -v $(pwd)/load-tests:/tests \
  -e BASE_URL=http://host.docker.internal:8090 \
  grafana/k6 run /tests/smoke-test.js
```

**Expected output:**
```
✓ status is 200
✓ authorized or declined

checks.....................: 100.00% ✓ 20 ✗ 0
http_req_duration..........: avg=15.23ms min=10.1ms med=14.5ms max=25.3ms
```

### 4. Run Load Test

```bash
# Terminal 3: Full load test with Grafana monitoring
docker-compose -f docker-compose.loadtest.yml up
```

**Test stages:**

| Time | VUs | Behavior |
|------|-----|----------|
| 0-30s | 10→50 | Ramp up |
| 30-90s | 50 | Steady load |
| 90-120s | 50→100 | Peak load (triggers rate limit) |
| 120-150s | 100→0 | Ramp down |

### 5. Observe in Grafana

Open http://localhost:3000/d/payment-gateway/payment-gateway

Watch these panels during test:

| Panel | Expected | Warning |
|-------|----------|---------|
| **QPS** | Linear growth | Plateau = rate limit |
| **P99 Latency** | Stable < 200ms | Spike > 500ms |
| **Throughput by Status** | Mostly AUTHORIZED | REJECTED increase |
| **Rate Limit Hits** | 0 at low VUs | Spikes at 100 VUs |

### 6. Analyze Results

**k6 output summary:**
```
http_req_duration........: avg=45.2ms min=12.1ms med=38.5ms max=523ms p(95)=120ms p(99)=350ms
http_reqs................: 2456 total
rate_limit_hits..........: 312 total
```

**PromQL verification:**
```promql
# Actual QPS during test
sum(rate(payment_total[1m]))

# Rate limit percentage
sum(rate(rate_limit_rejected_total[1m])) / sum(rate(payment_total[1m])) * 100

# P99 at peak
histogram_quantile(0.99, sum(rate(payment_latency_seconds_bucket[5m])) by (le))
```

## Test Scenarios

| File | VUs | Duration | Purpose | Rate Limit |
|------|-----|----------|---------|------------|
| `smoke-test.js` | 1 | 10s | Sanity check | No |
| `load-test.js` | 10→50→100 | 2.5m | Performance baseline | **Yes** |

## Rate Limit Behavior

Our API has **10 req/s per merchant** limit.

At 100 VUs (100 merchants):
- Each merchant: ~1 req/s (under limit)
- Total: ~100 req/s (no rate limit)

At 100 VUs (same merchant):
- Single merchant: ~100 req/s (**over limit**)
- ~90% requests rate limited
- k6 detects `Rejected` + `Rate limit` response
- Automatic 1s backoff applied

**Current script uses `merchant-${__VU}`** → each VU = different merchant → minimal rate limit.

To test rate limiting, modify script:
```javascript
const merchantId = 'merchant-fixed';  // Same merchant for all VUs
```

## Cleanup

```bash
# Stop load test
docker-compose -f docker-compose.loadtest.yml down

# Stop observability
docker-compose -f docker-compose.observability.yml down

# Stop application
docker-compose down
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| k6 cannot connect | Check `BASE_URL`, use `host.docker.internal` |
| No metrics in Grafana | Verify Prometheus targets, check `/actuator/prometheus` |
| Rate limit too aggressive | Adjust `RateLimitAspect` MAX_PER_SECOND |
| Out of memory | Reduce VUs or add JVM heap flags |
