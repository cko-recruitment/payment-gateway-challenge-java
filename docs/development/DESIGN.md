# Payment Gateway - Design Document

## How to Use This Document

Each section contains:
- **Concept**: Design principle
- **Demo Steps**: Exact commands to run
- **Verify**: How to confirm it works

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

### Concept: 200-Only REST API

All responses return HTTP 200. Business result is in the JSON body.

### Step 1: Start the Application

```bash
# Option 1: Docker Compose (Recommended - starts both app and bank simulator)
docker-compose up --build

# Option 2: Manual start
# Terminal 1: Start bank simulator
docker run -d --name bank-simulator \
  -p 8080:8080 \
  -v $(pwd)/imposters:/imposters \
  bbyars/mountebank:2.8.1 \
  --configfile /imposters/bank_simulator.ejs --allowInjection

# Wait for bank simulator
sleep 5

# Terminal 2: Start application
./gradlew bootRun
```

### Step 2: Test Successful Payment

```bash
# Request
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: key-1" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq .

# Expected Response
{
  "code": "SUCCESS",
  "description": "Payment authorized successfully",
  "action": "none",
  "links": [
    {
      "rel": "self",
      "href": "/api/v1/payments/550e8400-e29b-41d4-a716-446655440000",
      "method": "GET"
    },
    {
      "rel": "idempotency",
      "href": "/api/v1/payments/idempotency/key-1",
      "method": "GET"
    }
  ],
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 4241,
  "expiryMonth": 12,
  "expiryYear": 2027,
  "currency": "USD",
  "amount": 100
}
```

### Step 3: Test Validation Error (Still HTTP 200)

```bash
# Request with invalid card number
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: key-2" \
  -d '{"card_number":"abc","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq .

# Expected Response
{
  "code": "INVALID_REQUEST",
  "description": "Card number must be between 14 and 19 digits; Card number must only contain numeric characters",
  "action": "fix_request",
  "links": [
    {
      "rel": "self",
      "href": "/api/v1/payments",
      "method": "POST"
    }
  ],
  "status": "Rejected"
}
```

### Step 4: Test Idempotency

Use `createdAt` and `updatedAt` to trace request history:
- `createdAt == updatedAt` → First attempt or cached non-retriable
- `createdAt < updatedAt` → Retried (bank called again)

**Non-Retriable Error** (action=fix_request) - Cached forever:

```bash
KEY="fix-$(date +%s)"

# First request - validation error, cached
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: $KEY" \
  -d '{"card_number":"abc","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq '{createdAt, updatedAt, code, action}'
# → createdAt == updatedAt (first attempt)

# Same key - returns cached error (no retry)
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: $KEY" \
  -d '{"card_number":"abc","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq '{createdAt, updatedAt, code, action}'
# → createdAt == updatedAt (cached, same timestamps)
```

**Retriable Error** (action=retry) - Retried on next request:

```bash
KEY="retry-$(date +%s)"

# First request - bank returns 503, action=retry
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: $KEY" \
  -d '{"card_number":"4242424242424240","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq '{createdAt, updatedAt, code, action}'
# → createdAt == updatedAt (first attempt)

# Same key - retries bank call (bank still down)
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: $KEY" \
  -d '{"card_number":"4242424242424240","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq '{createdAt, updatedAt, code, action}'
# → createdAt < updatedAt (retried! updatedAt changed)
```

---

## 2. Code Design

### Concept: SOLID Principles + AOP

Clean separation of concerns using interfaces and aspects.

### Step 1: View Layered Architecture

```bash
# Open controller (HTTP layer)
open src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayController.java

# Open service (Business layer)
open src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java

# Open repository (Data layer)
open src/main/java/com/checkout/payment/gateway/repository/IdempotencyRepository.java
```

### Step 2: Verify Dependency Inversion

```bash
# Service depends on interface, not concrete class
grep "private final BankClient" src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java

# Output: private final BankClient bankClient;
# Not: private final BankSimulatorClient bankClient;
```

### Step 3: View AOP in Action

```bash
# Open rate limit aspect
open src/main/java/com/checkout/payment/gateway/aspect/RateLimitAspect.java

# Open metrics aspect
open src/main/java/com/checkout/payment/gateway/aspect/PaymentMetricsAspect.java

# Check that controller has no rate limit logic
grep -c "rateLimit\|RateLimit" src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayController.java

# Output: 0 (business code stays clean)
```

### Step 4: Verify Concurrent Idempotency

```bash
# Open the lock implementation
open src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java

# Search for lock usage
grep -n "ReentrantLock\|lock()\|unlock()" src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java
```

---

## 3. Testing Mindset

### Concept: Testing Pyramid

Unit (fast) → Integration (medium) → Functional (slow)

### Step 1: Run Unit Tests

```bash
# Fast tests, no external dependencies
./gradlew test

# Verify output
# Tests: 36, Passed: 36, Failed: 0

# View HTML report
open build/reports/tests/test/index.html
```

### Step 2: Run Integration Tests

```bash
# Requires bank simulator running
# (started in Step 1 of API Design)

./gradlew integrationTest

# Verify output
# Tests: 9, Passed: 9, Failed: 0

# View HTML report
open build/reports/tests/integrationTest/index.html
```

### Step 3: Run Functional Tests

```bash
# Requires application running
# (started in Step 1 of API Design)

./gradlew functionalTest

# Verify output
# Tests: 9, Passed: 9, Failed: 0

# View HTML report
open build/reports/tests/functionalTest/index.html
```

### Step 4: Check Coverage

```bash
# Generate combined coverage report
./gradlew jacocoFullReport

# View coverage report
open build/reports/jacoco/jacocoFullReport/html/index.html

# Check coverage percentage
grep -o 'Total[^%]*%' build/reports/jacoco/jacocoFullReport/html/index.html | head -1
```

### Step 5: Run Security Scan

```bash
# OWASP Dependency Check
./gradlew dependencyCheckAnalyze

# View report
open build/reports/dependency-check-report.html
```

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

### Concept: OpenAPI + Living Documentation

Auto-generated API docs from code annotations.

### Step 1: View OpenAPI Spec

```bash
# Start application (if not running)
./gradlew bootRun &

# Get OpenAPI JSON
curl -s http://localhost:8090/v3/api-docs | jq . | head -50

# Save to file
curl -s http://localhost:8090/v3/api-docs > openapi.json
```

### Step 2: View Swagger UI

```bash
# Open in browser
open http://localhost:8090/swagger-ui.html

# Or use curl
curl -s http://localhost:8090/swagger-ui.html | grep -o '<title>[^<]*'
```

### Step 3: Check API Annotations in Code

```bash
# View OpenAPI annotations
open src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayApi.java

# Check for @Operation, @ApiResponse annotations
grep -c "@Operation\|@ApiResponse" src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayApi.java
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

# Make a payment request
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: trace-demo" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}'

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
curl http://localhost:8090/api/v1/payments -X POST ...

# 3. Show idempotency (30s)
curl same request twice → same ID

# 4. Show tests (1m)
./gradlew test integrationTest functionalTest

# 5. Show metrics (30s)
curl http://localhost:8090/actuator/prometheus

# 6. Show traces (1m)
open http://localhost:16686

# 7. Show K8s config (30s)
kubectl apply -k k8s/overlays/dev/
```
