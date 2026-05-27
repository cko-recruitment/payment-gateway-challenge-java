# Payment Gateway - Design Document

## 1. API Design

### RESTful Principles

| Principle | Implementation |
|-----------|---------------|
| Resource-oriented | `/payments`, not `/processPayment` |
| HTTP verbs | POST (create), GET (read) |
| Statelessness | No server-side session |
| Idempotency | `Idempotency-Key` header for safe retries |

### Demo: 200-Only API

```bash
# Start the application
./gradlew bootRun

# Successful payment
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: key-1" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq .

# Response:
# {
#   "code": "SUCCESS",
#   "status": "Authorized",
#   "id": "550e8400-e29b-41d4-a716-446655440000"
# }

# Validation error (still HTTP 200)
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: key-2" \
  -d '{"card_number":"abc","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
  | jq .

# Response:
# {
#   "code": "INVALID_REQUEST",
#   "action": "fix_request",
#   "description": "Card number must only contain numeric characters"
# }
```

### Demo: Idempotency

```bash
# First request - processes payment
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: same-key" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}'

# Second request with same key - returns cached result (no duplicate charge)
curl -s http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: same-key" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}'

# Both return identical response with same ID
```

### Response Model

```
BasePaymentResponse
├── code          (machine-readable)
├── description   (human-readable)
├── action        (client guidance: none/retry/fix_request)
└── links[]       (HATEOAS for next actions)

PostPaymentResponse extends BasePaymentResponse
├── id            (UUID)
├── status        (Authorized/Declined/Rejected)
├── cardNumberLastFour
├── amount
└── currency
```

---

## 2. Code Design

### Layered Architecture

```
Controller (HTTP) → Service (Business) → Repository (Storage)
       ↓                    ↓                  ↘
  RateLimitAspect    PaymentMetricsAspect    BankClient
```

### Demo: SOLID Principles

```java
// Single Responsibility: Controller only handles HTTP
@RestController
public class PaymentGatewayController {
    @PostMapping("/payments")
    public ResponseEntity<PostPaymentResponse> processPayment(...) {
        // Validate headers, delegate to service
        PostPaymentResponse response = service.processPayment(...);
        // Add HATEOAS links
        return ResponseEntity.ok(response);
    }
}

// Dependency Inversion: Service depends on interface
@Service
public class PaymentGatewayService {
    private final BankClient bankClient;  // Interface, not concrete class
    private final IdempotencyRepository repository;
    
    public PaymentGatewayService(BankClient bankClient, ...) {
        this.bankClient = bankClient;
    }
}

// Open/Closed: New bank integration without changing service
public class NewBankClient implements BankClient {
    @Override
    public BankPaymentResponse processPayment(PostPaymentRequest request) {
        // New bank implementation
    }
}
```

### Demo: AOP Cross-Cutting Concerns

```java
// Business code stays clean - no rate limit logic here
@RateLimited  // Just an annotation
@PostMapping("/payments")
public ResponseEntity<PostPaymentResponse> processPayment(...) { }

// Rate limiting implemented in Aspect
@Aspect
@Component
public class RateLimitAspect {
    @Around("@annotation(RateLimited)")
    public Object checkLimit(ProceedingJoinPoint pjp) throws Throwable {
        String merchantId = extractMerchantId(pjp);
        if (!rateLimiter.allow(merchantId)) {
            throw new RateLimitExceededException();
        }
        return pjp.proceed();
    }
}
```

### Demo: Concurrent Idempotency

```java
public PostPaymentResponse processPayment(String merchantId, ..., String dedupeKey) {
    String lockKey = merchantId + ":" + dedupeKey;
    ReentrantLock lock = locks.computeIfAbsent(lockKey, k -> new ReentrantLock());
    lock.lock();
    try {
        // Double-check after acquiring lock
        Optional<PostPaymentResponse> existing = repository.getByKey(merchantId, dedupeKey);
        if (existing.isPresent() && !isRetriable(existing.get())) {
            return existing.get();  // Return cached result
        }
        // Process payment...
    } finally {
        lock.unlock();
        locks.remove(lockKey);
    }
}
```

---

## 3. Testing Mindset

### Testing Pyramid

```
         ┌─────────┐
         │   E2E   │  ← Functional Tests (9 tests, ~30s)
         │  (Slow) │     Full API flow with real HTTP
        ┌┴─────────┴┐
        │ Integration │  ← Integration Tests (9 tests, ~10s)
        │   (Medium)  │     Service + Bank Simulator
       ┌┴─────────────┴┐
       │     Unit       │  ← Unit Tests (36 tests, ~2s)
       │    (Fast)      │     Isolated, mocked
       └────────────────┘
```

### Demo: Run All Tests

```bash
# Unit tests only (fast)
./gradlew test

# Integration tests (requires bank simulator)
docker run -d --name bank-simulator -p 8080:8080 bbyars/mountebank:2.8.1
./gradlew integrationTest

# Functional tests (full stack)
./gradlew functionalTest

# All tests with coverage
./gradlew test integrationTest functionalTest jacocoFullReport

# View coverage report
open build/reports/jacoco/jacocoFullReport/html/index.html
```

### Demo: Test Isolation

```java
// Unit test - fast, no Spring context
@WebMvcTest(PaymentGatewayController.class)
class PaymentGatewayControllerTest {
    @MockBean private PaymentGatewayService service;
    
    @Test
    void shouldReturnDedupeResponse() throws Exception {
        when(service.processPayment(any(), any(), any()))
            .thenReturn(mockResponse);
        
        mvc.perform(post("/api/v1/payments")...)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Authorized"));
    }
}

// Integration test - real Spring context
@SpringBootTest(webEnvironment = RANDOM_PORT)
class PaymentGatewayApiIntegrationTest {
    @Test
    void shouldProcessPaymentAndRetrieveById() {
        ResponseEntity<PostPaymentResponse> post = 
            restTemplate.postForEntity("/api/v1/payments", request, ...);
        
        UUID paymentId = post.getBody().getId();
        
        ResponseEntity<GetPaymentResponse> get = 
            restTemplate.getForEntity("/api/v1/payments/" + paymentId, ...);
        
        assertThat(get.getBody().getId()).isEqualTo(paymentId);
    }
}
```

### Demo: CI/CD Pipeline

```yaml
# .github/workflows/ci.yml
name: CI
on: [pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Unit Tests
        run: ./gradlew test
      
      - name: Integration Tests
        run: ./gradlew integrationTest
      
      - name: Functional Tests
        run: ./gradlew functionalTest
      
      - name: Coverage Report
        run: ./gradlew jacocoFullReport
      
      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: build/reports/jacoco/
```

---

## 4. Packaging / Hosting

### Demo: Docker Build

```bash
# Build Docker image
./gradlew bootJar
docker build -t payment-gateway:latest .

# Run with Docker Compose
docker-compose up

# Verify
curl http://localhost:8090/actuator/health
```

### Demo: Kubernetes Deployment

```bash
# Apply base configuration
kubectl apply -k k8s/base/

# Apply dev overlay
kubectl apply -k k8s/overlays/dev/

# Check deployment
kubectl get pods -n payment-gateway
kubectl logs -n payment-gateway -l app=payment-gateway

# Port forward for testing
kubectl port-forward -n payment-gateway svc/payment-gateway 8090:80
```

### Demo: Istio Traffic Management

```bash
# Check Istio routing
kubectl get virtualservice -n payment-gateway

# Canary deployment: 10% traffic to new version
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: payment-gateway
spec:
  http:
  - route:
    - destination:
        host: payment-gateway
        subset: stable
      weight: 90
    - destination:
        host: payment-gateway
        subset: canary
      weight: 10
EOF
```

### Demo: Terraform Infrastructure

```bash
# Initialize Terraform
cd terraform/aws
terraform init

# Preview changes
terraform plan

# Create infrastructure
terraform apply

# Get kubeconfig
aws eks update-kubeconfig --region us-east-1 --name payment-gateway-cluster

# Deploy application
kubectl apply -k k8s/overlays/prod/
```

---

## 5. Technical Documentation

### Demo: OpenAPI Auto-Generation

```bash
# Start application
./gradlew bootRun

# Get OpenAPI spec
curl http://localhost:8090/v3/api-docs | jq .

# View Swagger UI
open http://localhost:8090/swagger-ui.html
```

### Demo: API Examples in Code

```java
@Operation(summary = "Process a new payment")
@ApiResponse(responseCode = "200", description = "Payment authorized",
    content = @Content(
        schema = @Schema(implementation = PostPaymentResponse.class),
        examples = @ExampleObject(name = "authorized", value = """
            {
              "code": "SUCCESS",
              "status": "Authorized",
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "cardNumberLastFour": 4241,
              "amount": 100,
              "currency": "USD"
            }
            """)))
public ResponseEntity<PostPaymentResponse> processPayment(...) { }
```

---

## 6. Observability

### Demo: Metrics (Prometheus + Grafana)

```bash
# Start with observability stack
docker-compose -f docker-compose.yml -f docker-compose.observability.yml up

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Query payment latency
open http://localhost:9090/graph?g0.expr=payment_process_seconds&g0.tab=0

# View Grafana dashboard
open http://localhost:3000
# Login: admin/admin
# Dashboard: Payment Gateway
```

### Demo: Distributed Tracing (Jaeger)

```bash
# Start with tracing
docker-compose -f docker-compose.yml -f docker-compose.tracing.yml up

# Make a payment request
curl http://localhost:8090/api/v1/payments \
  -X POST \
  -H "Merchant-Id: merchant-123" \
  -H "Idempotency-Key: trace-key" \
  -d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}'

# View trace in Jaeger
open http://localhost:16686
# Service: payment-gateway
# Find traces for POST /api/v1/payments
```

### Demo: Health Checks

```bash
# Overall health
curl http://localhost:8090/actuator/health | jq .
# {
#   "status": "UP",
#   "components": {
#     "bankSimulator": { "status": "UP" },
#     "diskSpace": { "status": "UP" },
#     "ping": { "status": "UP" }
#   }
# }

# Liveness probe (Kubernetes)
curl http://localhost:8090/actuator/health/liveness
# { "status": "UP" }

# Readiness probe (Kubernetes)
curl http://localhost:8090/actuator/health/readiness
# { "status": "UP" }

# Prometheus metrics
curl http://localhost:8090/actuator/prometheus | grep payment
# payment_process_seconds_count 42
# payment_process_seconds_sum 1.234
```

---

## Interview Demo Script

```bash
# 1. Start everything
docker-compose up

# 2. Show API design
curl http://localhost:8090/api/v1/payments -X POST ...

# 3. Show idempotency
curl same request twice → same ID

# 4. Show tests
./gradlew test integrationTest functionalTest

# 5. Show metrics
curl http://localhost:8090/actuator/prometheus

# 6. Show traces
open http://localhost:16686

# 7. Show K8s deployment
kubectl apply -k k8s/overlays/dev/
```
