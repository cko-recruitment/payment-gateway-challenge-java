# Payment Gateway - Design Document

## How to Use This Document

Each section contains:
- **Concept**: Design principle
- **Demo Steps**: Exact commands to run
- **Verify**: How to confirm it works

### Prerequisites

1. Install VS Code extension **REST Client** (humao.rest-client)
2. Open `demo.http` file in VS Code
3. Click **Send Request** above any request, or press `Cmd+Alt+R` (Mac)

### Before Each Demo: Clean Environment

```bash
# Stop docker-compose if running
docker-compose down 2>/dev/null || true

# Kill any running Java processes (Spring Boot app)
kill $(lsof -t -i:8090) 2>/dev/null || true

# Kill any running bank simulator containers (including old ones)
docker stop bank-simulator 2>/dev/null || true
docker rm bank-simulator 2>/dev/null || true

# If port 8080 is still in use, kill the process
kill $(lsof -t -i:8080) 2>/dev/null || true

# Wait for cleanup
sleep 2

# Verify ports are free
lsof -i :8090 || echo "Port 8090 is free"
lsof -i :8080 || echo "Port 8080 is free"
```

---

## 1. API Design

### Concept

| Decision | Why |
|---------|-----|
| **200-Only** | HTTP status = transport, business result in body. Simplifies client error handling. |
| **Idempotency Key** | Same key → same result. Prevents duplicate charges on retry. |
| **Action Field** | `none`/`retry`/`fix_request` tells client what to do next. |
| **createdAt/updatedAt** | Trace retry history without external IDs. |

### Start Application

```bash
docker-compose up --build
```

### Demo Steps (use `demo.http` in VS Code)

| Step | Request | What to Show |
|-----|---------|-------------|
| 2 | `Step2_SuccessPayment` | SUCCESS, id |
| 3 | `Step3_CacheHit` | Same id = cache hit |
| 4 | `Step4_ValidationError` | INVALID_REQUEST, action=fix_request |
| 5 | `Step5_ValidationCacheHit` | Same createdAt = cached |
| 6 | `Step6_RetriableError` | BANK_ERROR, action=retry |
| 7 | `Step7_RetryAgain` | createdAt < updatedAt = retried |
| 9 | `Step9_GetById` | Retrieve by payment ID |
| 10 | `Step10_GetByIdempotencyKey` | Retrieve by dedupe key |

### Idempotency Tracing

| Scenario | createdAt | updatedAt | Meaning |
|---------|-----------|-----------|---------|
| First success | 10:00:00 | 10:00:00 | New payment |
| Cache hit | 10:00:00 | 10:00:00 | Returned from cache |
| First 503 | 10:00:00 | 10:00:00 | Bank down, cached error |
| Retry 503 | 10:00:00 | 10:00:05 | Bank called again, still down |
| Retry success | 10:00:00 | 10:00:05 | Bank called again, now OK |

---

## 2. Code Design

### Concept: Separation of Concerns

Business logic ≠ HTTP logic ≠ Cross-cutting concerns. Each has its own layer.

### Architecture

```
  Controller (HTTP)  →  Service (Business)  →  Repository (Data)
        │                      │                      │
        │                      ▼                      │
        │              BankClient (Interface)         │
        │                      │                      │
        │                      ▼                      │
        │         BankSimulatorClient (REST call)     │
        │                                             │
        ▼                                             ▼
   Request/Response                           In-Memory Cache
   (no business logic)                         (idempotency)
```

### Why This Design?

| Problem | Solution | Where |
|---------|----------|-------|
| Controller gets fat | Move business logic to Service | `PaymentGatewayService` |
| Hard to swap bank | Depend on interface, not implementation | `BankClient` interface |
| Rate limit scattered | AOP aspect wraps controller | `RateLimitAspect` |
| Concurrent duplicate charge | Lock per idempotency key | `ConcurrentHashMap<key, Lock>` |

### Walkthrough

```bash
# 1. Controller: thin, no business logic
wc -l src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayController.java
# → ~80 lines, only HTTP handling

# 2. Service: all business logic in one place
wc -l src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java
# → ~200 lines, validation + cache + bank + lock

# 3. Interface: easy to swap implementations
grep "private final BankClient" src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java
# → BankClient, not BankSimulatorClient

# 4. AOP: add behavior without touching business code
grep -c "rateLimit\|RateLimit" src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayController.java
# → 0 (nothing in controller)

# 5. Lock: concurrent idempotency protection
grep -n "ReentrantLock\|lock()\|unlock()" src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java
# → per-key lock in ConcurrentHashMap
```

---

## 3. Testing Mindset

### Concept: Testing Pyramid + CI as Quality Gate

Tests are not optional. Every PR must pass all layers before merge.

| Layer | Speed | What It Tests | CI Gate |
|-------|-------|---------------|---------|
| **Unit** | < 10s | Service, Aspect, Repository (with mocks) | `./gradlew test` |
| **Integration** | ~30s | Component interaction, real HTTP calls | `./gradlew integrationTest` |
| **Functional** | ~30s | End-to-end HTTP flow | `./gradlew functionalTest` |
| **Coverage** | — | Combined ≥ 70% | `jacocoFullReport` |

### CI Workflow (`.github/workflows/ci.yml`)

```
PR opened/pushed
    │
    ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  Unit Tests │ → │ Integration │ → │ Functional  │
│  36 tests   │    │  9 tests    │    │  9 tests    │
└─────────────┘    └─────────────┘    └─────────────┘
    │
    ▼
┌─────────────┐    ┌─────────────┐
│   Coverage  │ → │   Report    │
│   ≥ 70%     │    │  PR Comment │
└─────────────┘    └─────────────┘
```

### Demo: Run Tests Locally

```bash
# All tests (same as CI)
./gradlew test integrationTest functionalTest

# With coverage
./gradlew jacocoFullReport

# View report
open build/reports/jacoco/jacocoFullReport/html/index.html
```

### Demo: View CI Report

Open any PR → CI posts combined test + coverage report as comment:

| Test Type | Total | ✅ Passed | ❌ Failed |
|-----------|-------|-----------|-----------|
| 🟢 Unit Tests | 36 | 36 | 0 |
| 🟢 Integration Tests | 9 | 9 | 0 |
| 🟢 Functional Tests | 9 | 9 | 0 |

| Test Scope | Coverage |
|------------|----------|
| Unit Tests | 🟢 65% |
| Integration Tests | 🟢 45% |
| Functional Tests | 🟢 40% |
| **Combined** | **🟢 85%** |

### Load Test

**1. Start app + observability stack**
```bash
# Start everything (app + bank + prometheus + grafana)
docker-compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

**2. Open Grafana**
```bash
open http://localhost:3000
# Login: admin / admin
# Dashboard: Payment Gateway
```

**3. Run load test**
```bash
# Option 1: Docker (no need to install k6)
docker-compose -f docker-compose.loadtest.yml run k6

# Option 2: Local k6 (if installed)
k6 run load-tests/load-test.js
```

**4. Watch metrics in real-time**
- Request rate (RPS)
- Response latency (p50/p95/p99)
- Error rate
- Rate limit hits
- JVM memory / GC

**Load profile:**
| Stage | Duration | Users | Purpose |
|-------|----------|-------|---------|
| Ramp up | 30s | 10 | Warm up |
| Steady | 1m | 50 | Normal load |
| Peak | 30s | 100 | Trigger rate limit |
| Ramp down | 30s | 0 | Cool down |

**Thresholds:**
- 95% requests < 500ms
- Error rate < 10% (excluding rate-limited requests)

---

## 4. Packaging / Hosting

### Concept: Docker + Kubernetes + Terraform

Containerized deployment with infrastructure as code.

### Step 1: Build Docker Image

```bash
# Build JAR
./gradlew bootJar

# Build Docker image
docker build -t payment-gateway:latest .

# Verify image
docker images | grep payment-gateway
```

### Step 2: Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f payment-gateway

# Test endpoint
curl http://localhost:8090/actuator/health
```

### Step 3: Deploy to Kubernetes

```bash
# Apply base configuration
kubectl apply -k k8s/base/

# Apply dev overlay
kubectl apply -k k8s/overlays/dev/

# Verify deployment
kubectl get pods -n payment-gateway
kubectl get svc -n payment-gateway
kubectl get ingress -n payment-gateway

# View logs
kubectl logs -n payment-gateway -l app=payment-gateway --tail=20

# Port forward for testing
kubectl port-forward -n payment-gateway svc/payment-gateway 8090:80

# Test
curl http://localhost:8090/actuator/health
```

### Step 4: View Terraform Configuration

```bash
# View AWS configuration
open terraform/main.tf

# View Aliyun configuration
open terraform/aliyun/main.tf

# View variables
open terraform/variables.tf
```

### Step 5: Verify Istio Configuration

```bash
# Check Istio resources
kubectl get virtualservice -n payment-gateway
kubectl get destinationrule -n payment-gateway
kubectl get gateway -n payment-gateway

# View details
kubectl describe virtualservice payment-gateway -n payment-gateway
```

---

## 5. Technical Documentation

### Concept: Living Documentation

Code is the single source of truth. Docs are generated from code, not maintained separately.

| Problem | Traditional | Living Docs |
|---------|------------|-------------|
| API spec outdated | Word/PDF, manual update | OpenAPI auto-generated from annotations |
| Frontend/backend out of sync | Postman collection | Swagger UI always matches code |
| Onboarding new dev | Read wiki | Read code + generated docs |

### Demo

**1. OpenAPI spec from code**
```bash
# Generated from @Operation, @ApiResponse annotations
curl -s http://localhost:8090/v3/api-docs | jq . | head -30
```

**2. Swagger UI always current**
```bash
# UI reflects latest code, never stale
open http://localhost:8090/swagger-ui.html
```

**3. Annotations in code**
```bash
open src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayApi.java
# → @Operation(summary = "...", description = "...")
# → @ApiResponse(responseCode = "200", examples = @ExampleObject(...))
```

**4. Generate client SDK from spec**
```bash
# Frontend team uses this, guaranteed to match backend
curl -s http://localhost:8090/v3/api-docs > openapi.json
# → Feed to openapi-generator to create TypeScript/Java client
```

---

## 6. Observability

### Concept: Metrics + Logs + Traces

Three pillars of observability with Prometheus, Grafana, Jaeger.

### Step 1: Start Observability Stack

```bash
# Start application with monitoring
docker-compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# Verify all services
docker-compose ps
```

### Step 2: Check Prometheus Metrics

In `demo.http`: **Step12_PrometheusMetrics** → Send Request

```bash
# View raw metrics
curl -s http://localhost:8090/actuator/prometheus | grep payment

# Expected output:
# payment_process_seconds_count 42
# payment_process_seconds_sum 1.234
# payment_process_seconds_max 0.089

# Query specific metric
curl -s http://localhost:9090/api/v1/query?query=payment_process_seconds_count | jq .
```

### Step 3: View Grafana Dashboard

```bash
# Open Grafana
open http://localhost:3000

# Login
# Username: admin
# Password: admin

# Navigate to Dashboards > Payment Gateway
```

### Step 4: View Health Checks

In `demo.http`: **Step1_HealthCheck** → Send Request

```bash
# Overall health
curl -s http://localhost:8090/actuator/health | jq .

# Liveness probe
curl -s http://localhost:8090/actuator/health/liveness

# Readiness probe
curl -s http://localhost:8090/actuator/health/readiness
```

### Step 5: View Distributed Traces

```bash
# Start with tracing enabled
docker-compose -f docker-compose.yml -f docker-compose.tracing.yml up -d

# Make a payment request (use demo.http Step2)

# Open Jaeger UI
open http://localhost:16686

# Steps in Jaeger:
# 1. Select Service: payment-gateway
# 2. Click "Find Traces"
# 3. Click on a trace to see full request flow
```

---

## Quick Interview Demo (5 minutes)

```bash
# 1. Start everything (30s)
docker-compose up -d

# 2. Show API (30s)
# Use demo.http: Step2_SuccessPayment → Send Request

# 3. Show idempotency (30s)
# demo.http: Step3_CacheHit → Send Request (same ID)

# 4. Show tests (1m)
./gradlew test integrationTest functionalTest

# 5. Show metrics (30s)
# demo.http: Step12_PrometheusMetrics → Send Request

# 6. Show traces (1m)
open http://localhost:16686

# 7. Show K8s config (30s)
kubectl apply -k k8s/overlays/dev/
```
