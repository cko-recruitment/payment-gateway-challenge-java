# Observability Stack

Prometheus + Grafana for monitoring Payment Gateway metrics.

## Quick Start

```bash
# Start payment gateway + bank simulator
docker-compose up -d

# Start observability stack (Prometheus + Grafana)
docker-compose -f docker-compose.observability.yml up -d
```

## Access

| Service | URL |
|---------|-----|
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |

## Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `payment_total` | Counter | Payments by status (AUTHORIZED, DECLINED, REJECTED) |
| `payment_latency_seconds` | Timer | Payment processing latency |
| `bank_latency_seconds` | Timer | Bank API call latency |
| `payment_dedupe_hit` | Counter | Idempotency deduplication hits |
| `http_server_requests_seconds` | Timer | HTTP request latency (Spring) |

## PromQL Examples

```promql
# QPS
sum(rate(payment_total[1m]))

# P99 Payment Latency
histogram_quantile(0.99, rate(payment_latency_seconds_bucket[5m]))

# P99 Bank Latency
histogram_quantile(0.99, rate(bank_latency_seconds_bucket[5m]))

# Payment by Status
sum by (status) (rate(payment_total[1m]))

# Error Rate
sum(rate(payment_total{status="REJECTED"}[1m])) / sum(rate(payment_total[1m]))
```

## Stop

```bash
docker-compose -f docker-compose.observability.yml down
```
