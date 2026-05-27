# Payment Gateway - Design Document

## 1. Observability

### Three Pillars

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│     Metrics     │  │      Logs       │  │    Tracing      │
│                 │  │                 │  │                 │
│  Prometheus     │  │  Structured     │  │  OpenTelemetry  │
│  + Grafana      │  │  JSON           │  │  + Jaeger       │
│                 │  │                 │  │                 │
│  - Latency P99  │  │  - Masked PII   │  │  - Request flow │
│  - Error rate   │  │  - CorrelationID│  │  - Service deps │
│  - Throughput   │  │  - Timestamp    │  │  - Bottlenecks  │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Implementation

| Layer | Tool | Purpose |
|-------|------|---------|
| Application | Micrometer + Prometheus | JVM metrics, custom business metrics |
| Infrastructure | Grafana dashboards | Visualization, alerting |
| Distributed | OpenTelemetry + Jaeger | End-to-end request tracing |

### Key Metrics

```java
// Payment latency histogram
@Timed(value = "payment.process", percentiles = {0.5, 0.95, 0.99})
public PostPaymentResponse processPayment(...) { }

// Rate limit counter
@Counted(value = "rate.limit.hit")
public void checkLimit(...) { }
```

### Health Checks

```
/actuator/health          → Overall status
/actuator/health/liveness  → Kubernetes liveness probe
/actuator/health/readiness → Kubernetes readiness probe
/actuator/prometheus      → Prometheus scrape endpoint
```

---

## 2. Packaging / Hosting

### Container Strategy

```
┌─────────────────────────────────────┐
│         Docker Image                 │
│  ┌─────────────────────────────┐    │
│  │  Eclipse Temurin JRE 17      │    │
│  │  ┌─────────────────────┐    │    │
│  │  │  Spring Boot App    │    │    │
│  │  │  (Layered JAR)      │    │    │
│  │  └─────────────────────┘    │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Why Layered JAR:**
- Dependencies layer (rarely changes) → Docker cache hit
- Application layer (frequently changes) → Fast rebuild

### Deployment Options

| Environment | Tool | Configuration |
|-------------|------|---------------|
| Local | Docker Compose | `docker-compose.yml` |
| Dev / Test | Kind / Minikube | `k8s/overlays/dev/` |
| Production | EKS / ACK | `k8s/overlays/prod/` |

### Kubernetes Architecture

```
┌─────────────────────────────────────────┐
│              Ingress                     │
│         (TLS termination)                │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────┼───────────────────────┐
│                 ▼                       │
│  ┌─────────────────────────────────┐    │
│  │     Istio Gateway                │    │
│  │  (Traffic management, mTLS)      │    │
│  └─────────────────────────────────┘    │
│                 │                       │
│  ┌──────────────┼──────────────┐       │
│  │              ▼              │       │
│  │  ┌─────────┐  ┌─────────┐  │       │
│  │  │ Pod 1   │  │ Pod 2   │  │       │
│  │  │ + Envoy │  │ + Envoy │  │       │
│  │  └─────────┘  └─────────┘  │       │
│  │       ↑            ↑        │       │
│  │    Service Mesh (Istio)     │       │
│  └─────────────────────────────┘       │
└─────────────────────────────────────────┘
```

### Infrastructure as Code

```hcl
# terraform/main.tf
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"
  
  cluster_name    = "payment-gateway"
  cluster_version = "1.29"
  
  # VPC, subnets, node groups...
}
```

**Multi-Cloud Strategy:**
- China users → Aliyun (Beijing/Shanghai)
- Global users → AWS (Singapore/Frankfurt)
- Data residency compliance per region

---

## 3. Technical Documentation

### API Documentation

| Tool | Purpose | URL |
|------|---------|-----|
| OpenAPI 3.0 | API specification | `/v3/api-docs` |
| Swagger UI | Interactive docs | `/swagger-ui.html` |
| Examples | Request/response samples | Embedded in annotations |

```java
@Operation(summary = "Process a new payment")
@ApiResponse(responseCode = "200", description = "Payment authorized",
    content = @Content(schema = @Schema(implementation = PostPaymentResponse.class),
        examples = @ExampleObject(name = "authorized", value = "...")))
```

### Code Documentation

- **README.md**: Quick start, architecture overview
- **DESIGN.md**: Design decisions, trade-offs
- **RUN.md**: Local development, troubleshooting
- **K8S.md**: Kubernetes deployment guide
- **Inline comments**: Complex business logic only

### Living Documentation

```yaml
# .github/workflows/ci.yml
- name: Update API Docs
  run: |
    # Auto-generate OpenAPI spec from code
    ./gradlew generateOpenApiDocs
    # Publish to GitHub Pages
```

---

## 4. API Design

### RESTful Principles

| Principle | Implementation |
|-----------|---------------|
| Resource-oriented | `/payments`, not `/processPayment` |
| HTTP verbs | POST (create), GET (read) |
| Statelessness | No server-side session |
| Idempotency | `Idempotency-Key` header for safe retries |

### The "200-Only" Contract

```
POST /api/v1/payments
→ Always returns HTTP 200
→ Business result in JSON body

Success:    { "code": "SUCCESS", "status": "Authorized" }
Declined:   { "code": "PAYMENT_DECLINED", "status": "Declined" }
Error:      { "code": "INVALID_REQUEST", "action": "fix_request" }
```

**Why 200-Only:**
- Simplified client parsing (no HTTP status switch)
- Unified error model across all responses
- Business outcomes separated from transport errors

### Idempotency Design

```
Client          Gateway           Bank
  │               │               │
  ├── POST ───────►│               │
  │  Idempotency   │               │
  │  Key: abc123   │               │
  │               ├── Check cache ─►│
  │               │  (miss)         │
  │               ├── Call bank ───►│
  │               │  (authorized)   │
  │               ◄─────────────────│
  │               ├── Save result   │
  │◄──────────────┤               │
  │  { status: Authorized }
  │               │               │
  ├── POST ───────►│               │
  │  Same Key      │               │
  │  abc123        │               │
  │               ├── Check cache ─►│
  │               │  (hit)          │
  │◄──────────────┤               │
  │  Same result   │               │
```

**Concurrent Safety:**
- Per-key `ReentrantLock` prevents duplicate processing
- Double-check after acquiring lock
- Retriable errors allow reprocessing (bank unavailable)

### Response Model

```
BasePaymentResponse
├── code          (machine-readable)
├── description   (human-readable)
├── action        (client guidance)
└── links[]       (HATEOAS)

PostPaymentResponse extends BasePaymentResponse
├── id            (UUID)
├── status        (Authorized/Declined/Rejected)
├── cardNumberLastFour
├── amount
└── currency

GetPaymentResponse extends PostPaymentResponse
```

---

## 5. Code Design

### Layered Architecture

```
┌─────────────────────────────────────┐
│  Controller (HTTP Layer)            │
│  - Request/response mapping         │
│  - Exception translation            │
│  - No business logic                │
├─────────────────────────────────────┤
│  Service (Business Layer)           │
│  - Validation                       │
│  - Orchestration                    │
│  - Idempotency                      │
├─────────────────────────────────────┤
│  Repository (Data Layer)            │
│  - In-memory storage                │
│  - Dual-indexed (ID + dedupe key)   │
├─────────────────────────────────────┤
│  Client (External Layer)            │
│  - Bank simulator integration       │
│  - Retry logic                      │
└─────────────────────────────────────┘
```

### SOLID Principles

| Principle | Application |
|-----------|-------------|
| **S**ingle Responsibility | Controller handles HTTP, Service handles business |
| **O**pen/Closed | `BankClient` interface allows new bank integrations |
| **L**iskov Substitution | `InMemoryIdempotencyRepository` implements `IdempotencyRepository` |
| **I**nterface Segregation | `PaymentGatewayApi` separates OpenAPI from implementation |
| **D**ependency Inversion | Service depends on `BankClient` interface, not concrete class |

### Cross-Cutting Concerns (AOP)

```java
@Aspect
@Component
public class RateLimitAspect {
    @Around("@annotation(RateLimited)")
    public Object checkLimit(ProceedingJoinPoint pjp) throws Throwable {
        // Check per-merchant limit
        // Proceed or throw RateLimitExceededException
    }
}
```

**Benefits:**
- Business code stays clean
- Metrics/logging non-invasive
- Swappable implementations

---

## 6. Testing Mindset

### Testing Pyramid

```
         ┌─────────┐
         │   E2E   │  ← Functional Tests (9 tests)
         │  (Slow) │     Full API flow
        ┌┴─────────┴┐
        │ Integration │  ← Integration Tests (9 tests)
        │   (Medium)  │     Service + external deps
       ┌┴─────────────┴┐
       │     Unit       │  ← Unit Tests (36 tests)
       │    (Fast)      │     Isolated, mocked
       └────────────────┘
```

### Test Strategy

| Type | Scope | Tools | When |
|------|-------|-------|------|
| **Unit** | Single class | JUnit, Mockito | Every commit |
| **Integration** | Service + DB/client | Spring Boot Test | PR |
| **Functional** | Full API | TestRestTemplate | PR |
| **Security** | Vulnerabilities | ZAP, Dependency-Check | PR |
| **Load** | Performance | k6 | Release |

### Test Isolation

```java
@WebMvcTest(PaymentGatewayController.class)
class PaymentGatewayControllerTest {
    @MockBean private PaymentGatewayService service;
    // Fast, no Spring context
}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class PaymentGatewayApiIntegrationTest {
    // Full context, real HTTP calls
}
```

### Coverage Strategy

```groovy
// build.gradle
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70  // 70% threshold
            }
        }
    }
}
```

**Excluded from coverage:**
- Swagger annotations (`PaymentGatewayApi`)
- POJO models (`model/**`, `enums/**`)
- Configuration classes
- Main application class

### CI/CD Quality Gates

```
PR Push
    ↓
┌─────────────┐
│  Unit Tests  │ → 36 tests, must pass
│  (36 tests)  │
└──────┬──────┘
       ↓
┌─────────────┐
│ Integration  │ → 9 tests, bank simulator required
│   (9 tests)  │
└──────┬──────┘
       ↓
┌─────────────┐
│  Functional  │ → 9 tests, full stack
│   (9 tests)  │
└──────┬──────┘
       ↓
┌─────────────┐
│   Coverage   │ → 70% minimum
│   (JaCoCo)   │
└──────┬──────┘
       ↓
┌─────────────┐
│   Security   │ → CodeQL + OWASP ZAP
│   (SAST/DAST)│
└──────┬──────┘
       ↓
    Merge
```

---

## Interview Summary

**Key Talking Points:**

1. **Observability**: Three pillars (metrics, logs, traces) with Prometheus/Grafana/Jaeger
2. **Packaging**: Docker layered JAR, K8s with Istio, Terraform for multi-cloud
3. **Documentation**: OpenAPI auto-generated, living docs in CI
4. **API Design**: 200-Only contract, idempotency with concurrent safety
5. **Code Design**: SOLID principles, AOP for cross-cutting concerns
6. **Testing**: Pyramid strategy, 70% coverage, security scanning in CI
