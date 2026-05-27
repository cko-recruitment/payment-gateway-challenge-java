# Observability Stack

Prometheus + Grafana for monitoring Payment Gateway metrics.

## Architecture

```
┌─────────────────┐     scrape      ┌─────────────────┐
│   Prometheus    │ ◄────────────── │  Payment Gateway │
│   (Server)      │   /actuator/    │   /actuator/     │
│                 │   prometheus    │   prometheus     │
└────────┬────────┘                 └─────────────────┘
         │
         │ query
         ▼
┌─────────────────┐
│    Grafana      │ ◄── Dashboard & Alerts
│   (Web UI)      │
└─────────────────┘
```

## Components

| Component | Role |
|-----------|------|
| **Prometheus** | Scrape metrics, store time-series data, evaluate alert rules |
| **Grafana** | Visualize query results (charts, dashboards) |
| **Alertmanager** | Alert notifications (email, Slack, PagerDuty) |

## Quick Start

```bash
# Start payment gateway + bank simulator
docker-compose up -d

# Start observability stack (Prometheus + Grafana)
docker-compose -f docker-compose.observability.yml up -d
```

## Access

| Service | URL | Default Credentials |
|---------|-----|---------------------|
| Grafana | http://localhost:3000 | admin/admin |
| Prometheus | http://localhost:9090 | - |

## Available Metrics

### Business Metrics (Custom)

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `payment_total` | Counter | `status` (AUTHORIZED, DECLINED, REJECTED) | Payments processed |
| `payment_latency_seconds` | Timer | `status` | Payment processing latency |
| `bank_latency_seconds` | Timer | - | Bank API call latency |
| `payment_dedupe_hit` | Counter | - | Idempotency deduplication hits |

### System Metrics (Spring Boot Actuator)

| Metric | Type | Description |
|--------|------|-------------|
| `http_server_requests_seconds` | Timer | HTTP request latency |
| `jvm_memory_used_bytes` | Gauge | JVM memory usage |
| `jvm_gc_pause_seconds` | Timer | GC pause time |
| `system_cpu_usage` | Gauge | CPU usage |
| `process_uptime_seconds` | Gauge | Application uptime |

### Health Checks

| Endpoint | URL | Purpose |
|----------|-----|---------|
| Health | `/actuator/health` | Overall status |
| Liveness | `/actuator/health/liveness` | Kubernetes liveness probe |
| Readiness | `/actuator/health/readiness` | Kubernetes readiness probe |
| Bank Health | `/actuator/health/bankSimulator` | Downstream bank availability |

## PromQL Examples

### Basic Queries

```promql
# QPS (Queries Per Second)
sum(rate(payment_total[1m]))

# Payment by Status
sum by (status) (rate(payment_total[1m]))

# Total Requests
sum(rate(http_server_requests_seconds_count[1m]))
```

### Latency Percentiles

```promql
# P99 Payment Latency
histogram_quantile(0.99, 
  rate(payment_latency_seconds_bucket[5m])
)

# P95 Bank Latency
histogram_quantile(0.95,
  rate(bank_latency_seconds_bucket[5m])
)

# P99 HTTP Latency by URI
histogram_quantile(0.99,
  sum by (uri, le) (
    rate(http_server_requests_seconds_bucket[5m])
  )
)
```

### Error Analysis

```promql
# Error Rate
sum(rate(payment_total{status="REJECTED"}[1m])) 
/ sum(rate(payment_total[1m]))

# HTTP 5xx Rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))
/ sum(rate(http_server_requests_seconds_count[1m]))

# Bank Errors
sum(rate(bank_latency_seconds_count{error!="none"}[1m]))
```

### Resource Usage

```promql
# JVM Memory Usage
jvm_memory_used_bytes / jvm_memory_max_bytes

# CPU Usage
system_cpu_usage

# GC Pause Time
rate(jvm_gc_pause_seconds_sum[5m])
```

## Grafana Dashboard

### Pre-configured Panels

| Panel | Query | Purpose |
|-------|-------|---------|
| QPS | `sum(rate(payment_total[1m]))` | Throughput |
| P99 Latency | `histogram_quantile(0.99, ...)` | Latency SLA |
| Error Rate | Error / Total ratio | Reliability |
| JVM Memory | `jvm_memory_used_bytes` | Resource usage |

### Custom Dashboard Tips

1. **Variables**: Use `status` tag to filter by payment status
2. **Alerts**: Set thresholds on P99 latency (> 500ms) and error rate (> 1%)
3. **Time Range**: Use 5m for real-time, 1h for trends

## Prometheus Configuration

```yaml
# observability/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'payment-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['payment-gateway:8090']
```

## Integration with Load Testing

During load tests, watch Grafana for:

| Metric | Expected Behavior | Warning Sign |
|--------|-------------------|--------------|
| QPS | Linear growth with VUs | Plateau or drop |
| P99 Latency | Stable under load | Sudden spike |
| Error Rate | Near zero | Increase > 1% |
| JVM Memory | Stable or slow growth | Rapid increase |
| GC Pauses | < 100ms | > 500ms |

## Stop

```bash
# Stop observability stack
docker-compose -f docker-compose.observability.yml down

# Stop application
docker-compose down
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| No metrics in Prometheus | Check `/actuator/prometheus` is accessible |
| Grafana shows "No data" | Verify Prometheus datasource URL |
| Custom metrics missing | Ensure `MeterRegistry` is injected |
| High cardinality | Avoid unbounded tags (e.g., user ID) |
