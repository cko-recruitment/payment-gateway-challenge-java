# Payment Gateway Architecture Report

## Executive Summary

This document describes the architecture of the Payment Gateway system, designed with cloud-native principles. The system follows a **layered architecture** with clear separation of concerns, implementing **12-Factor App** methodology for cloud deployment.

---

## 1. System Architecture

### 1.1 High-Level Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Clients                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │   Web App    │  │  Mobile App  │  │  Merchant API │                  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                  │
└─────────┼─────────────────┼─────────────────┼──────────────────────────┘
          │                 │                 │
          └─────────────────┼─────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Ingress (Nginx)                                  │
│                    TLS Termination / Rate Limiting                       │
└─────────────────────────────────────────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                                    │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     Payment Gateway Pods                         │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │    │
│  │  │   Pod 1     │  │   Pod 2     │  │   Pod N     │  ◄── HPA   │    │
│  │  │  (Replica)  │  │  (Replica)  │  │  (Replica)  │             │    │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │    │
│  └─────────┼────────────────┼────────────────┼────────────────────┘    │
│            └────────────────┼────────────────┘                         │
│                             ▼                                          │
│                    ┌─────────────────┐                                 │
│                    │   Service       │                                 │
│                    │  (ClusterIP)    │                                 │
│                    └────────┬────────┘                                 │
└─────────────────────────────┼──────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Observability Stack                               │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐             │
│  │  Prometheus │ ───► │   Grafana   │      │   Alert     │             │
│  │  (Metrics)  │      │ (Dashboard) │      │  Manager    │             │
│  └─────────────┘      └─────────────┘      └─────────────┘             │
│                                                                         │
│  ┌─────────────┐      ┌─────────────┐                                   │
│  │    Loki     │ ───► │   Grafana   │  (Logs)                          │
│  │   (Logs)    │      │ (Log Query) │                                   │
│  └─────────────┘      └─────────────┘                                   │
└─────────────────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       External Dependencies                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │  Bank Simulator │  │   Prometheus    │  │    Grafana      │         │
│  │  (HTTP API)     │  │   (Scrape)      │  │   (Visualize)   │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Application Layers                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     Controller Layer                             │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │           PaymentGatewayController                      │    │    │
│  │  │  - POST /api/v1/payments                                │    │    │
│  │  │  - GET  /api/v1/payments/{id}                           │    │    │
│  │  │  - GET  /api/v1/payments/idempotency/{key}              │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                              ▲                                          │
│                              │ HTTP Request                             │
│  ┌───────────────────────────┼─────────────────────────────────────┐   │
│  │                     AOP Layer (Cross-Cutting)                    │   │
│  │  ┌─────────────────┐  ┌─────────────────────────────────────┐  │   │
│  │  │ RateLimitAspect │  │      PaymentMetricsAspect           │  │   │
│  │  │ - Per-merchant  │  │  - payment.total counter            │  │   │
│  │  │   rate limiting │  │  - payment.latency timer            │  │   │
│  │  │ - 10 req/s      │  │  - bank.latency timer               │  │   │
│  │  └─────────────────┘  └─────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                      Service Layer                               │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │           PaymentGatewayService                         │    │    │
│  │  │  - Validation (card, currency, expiry)                  │    │    │
│  │  │  - Bank interaction                                     │    │    │
│  │  │  - Idempotency handling                                 │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     Repository Layer                             │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │      InMemoryIdempotencyRepository                      │    │    │
│  │  │  - Dual-indexed storage (ID + Idempotency Key)          │    │    │
│  │  │  - O(1) retrieval performance                           │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                       Client Layer                               │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │         BankSimulatorClient                             │    │    │
│  │  │  - HTTP REST client for Bank Simulator                  │    │    │
│  │  │  - Circuit breaker ready (interface-based)              │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Technology Stack

### 2.1 Core Framework

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 17 | Modern LTS with records, pattern matching |
| Framework | Spring Boot | 3.1.5 | Auto-configuration, embedded server |
| Build Tool | Gradle | 8.2.1 | Flexible build scripting |
| API Docs | SpringDoc OpenAPI | 2.2.0 | Swagger UI generation |

### 2.2 Cloud-Native Stack

| Category | Technology | Purpose |
|----------|-----------|---------|
| Containerization | Docker | Application packaging |
| Orchestration | Kubernetes | Container scheduling |
| Configuration | Kustomize | Environment-specific manifests |
| Metrics | Micrometer + Prometheus | Application metrics |
| Visualization | Grafana | Dashboards and alerts |
| Load Testing | k6 | Performance testing |

### 2.3 CI/CD Stack

| Category | Technology | Purpose |
|----------|-----------|---------|
| CI Platform | GitHub Actions | Build automation |
| SAST | CodeQL | Static security analysis |
| Dependency Scan | OWASP Dependency-Check | CVE detection |
| DAST | OWASP ZAP | Dynamic security scanning |
| Coverage | JaCoCo | Code coverage reporting |

---

## 3. Design Principles

### 3.1 The "200-Only" API Contract

```
Traditional REST API          Our "200-Only" API
━━━━━━━━━━━━━━━━━━━          ━━━━━━━━━━━━━━━━━━

200 OK  ──► Success           200 OK ──► All responses
400 Bad Request ──► Invalid   {
401 Unauthorized ──► Auth        "code": "SUCCESS"
500 Server Error ──► Crash       "code": "INVALID_REQUEST"
                                 "code": "BANK_UNAVAILABLE"
                              }
```

**Rationale**: Clients handle a single response format. Business outcomes are in the body, not HTTP status codes.

### 3.2 Unified Response Model

```java
public class BasePaymentResponse {
    private String code;        // Machine-readable: SUCCESS, PAYMENT_DECLINED
    private String description; // Human-readable
    private String action;      // Recommended action: none, retry, fix_request
    private List<Link> links;   // HATEOAS for next actions
}
```

### 3.3 Idempotency Strategy

```
First Request                    Duplicate Request
━━━━━━━━━━━━━                    ━━━━━━━━━━━━━━━━

POST /api/v1/payments            POST /api/v1/payments
Idempotency-Key: key-123         Idempotency-Key: key-123
                                 
1. Check cache: miss             1. Check cache: HIT
2. Call bank                     2. Return cached response
3. Store result in cache         (No duplicate bank charge!)
4. Return response
```

---

## 4. Security Architecture

### 4.1 Defense in Depth

```
┌─────────────────────────────────────────────────────────────────┐
│                        Security Layers                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Layer 1: Network                                               │
│  ├── TLS 1.3 for all communications                             │
│  ├── NetworkPolicy (K8s) for pod-to-pod access control          │
│  └── Ingress rate limiting                                      │
│                                                                 │
│  Layer 2: Application                                           │
│  ├── Rate limiting (10 req/s per merchant)                      │
│  ├── Input validation (card number, currency, expiry)           │
│  ├── PII masking in logs (SecureLogger)                         │
│  └── Idempotency for duplicate protection                       │
│                                                                 │
│  Layer 3: Container                                             │
│  ├── Non-root user (runAsUser: 1000)                            │
│  ├── Read-only root filesystem                                  │
│  ├── Drop all capabilities                                      │
│  └── Distroless base image                                      │
│                                                                 │
│  Layer 4: CI/CD                                                 │
│  ├── SAST (CodeQL)                                              │
│  ├── Dependency vulnerability scanning                          │
│  ├── DAST (OWASP ZAP)                                           │
│  └── No secrets in code (GitLeaks)                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 PII Protection

```java
// Request toString masks sensitive data
PostPaymentRequest{
    cardNumber='[MASKED]'           // Never log full card number
    expiryMonth=12
    expiryYear=2027
    currency='USD'
    amount=100
    cvv='[MASKED]'                  // Never log CVV
}
```

---

## 5. Observability Architecture

### 5.1 Three Pillars

```
┌─────────────────────────────────────────────────────────────────┐
│                    Observability Stack                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Metrics (Prometheus)        Logs (Structured)      Traces      │
│  ━━━━━━━━━━━━━━━━━━━━        ━━━━━━━━━━━━━━━━       ━━━━━━      │
│                                                                 │
│  payment_total{status}       Processing payment     [Future]    │
│  payment_latency_seconds     Bank call failed                 │
│  rate_limit_rejected_total   Rate limit exceeded              │
│  bank_latency_seconds        Dedupe hit                       │
│                                                                 │
│  Scraped by Prometheus       Output to stdout/Loki            │
│  Visualized in Grafana       Queried in Grafana               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Health Probes

| Probe | Endpoint | Purpose | Action on Failure |
|-------|----------|---------|-------------------|
| Liveness | `/actuator/health/liveness` | Is the process alive? | Restart container |
| Readiness | `/actuator/health/readiness` | Is the app ready for traffic? | Remove from Service |
| Custom (Bank) | `/actuator/health/bankSimulator` | Is the bank reachable? | Mark degraded |

---

## 6. Deployment Architecture

### 6.1 Kustomize Structure

```
k8s/
├── base/                          # Shared base configuration
│   ├── configmap.yaml             # Application config (bank URL, JVM opts)
│   ├── deployment.yaml            # Pod template with security context
│   ├── service.yaml               # ClusterIP service
│   ├── ingress.yaml               # External access
│   ├── hpa.yaml                   # Auto-scaling (2-10 replicas)
│   ├── pdb.yaml                   # Rolling update protection
│   └── kustomization.yaml
│
└── overlays/                      # Environment-specific patches
    ├── dev/
    │   ├── kustomization.yaml     # 1 replica, latest image, low resources
    │   └── patch-resources.yaml   # 128Mi/256Mi memory
    │
    └── prod/
        ├── kustomization.yaml     # 3 replicas, versioned image
        └── patch-resources.yaml   # 512Mi/1Gi memory
```

### 6.2 Multi-Environment Deployment

```bash
# Development (1 replica, latest image, minimal resources)
kubectl apply -k k8s/overlays/dev

# Production (3 replicas, pinned version, full resources)
kubectl apply -k k8s/overlays/prod
```

### 6.3 Resource Allocation

| Environment | Replicas | Memory Request | Memory Limit | CPU Request | CPU Limit |
|-------------|----------|----------------|--------------|-------------|-----------|
| Dev | 1 | 128Mi | 256Mi | 100m | 250m |
| Prod | 3 | 512Mi | 1Gi | 500m | 1000m |

---

## 7. Testing Strategy

### 7.1 Test Pyramid

```
                    ┌─────────┐
                    │   E2E   │  Functional Tests (1)
                    │  (Slow) │  SpringBootTest + TestRestTemplate
                    ├─────────┤
                   ┌┤  Integration ├┐  Integration Tests (3)
                  ┌┘│   (Medium)   │┘  Real HTTP calls, real context
                 ┌┘ ├──────────────┤ ┘
                ┌┘  │     Unit     │  ┐  Unit Tests (6)
               ┌┘   │    (Fast)    │  ┘  Mockito, JUnit
              ┌┘    └──────────────┘   ┘
             ┌┘                        ┘
            └──────────────────────────┘
```

### 7.2 Coverage by Test Type

| Package | Unit Test | Integration Test | Functional Test | Combined |
|---------|-----------|------------------|-----------------|----------|
| service | 86% | +0% | +0% | 86% |
| aspect | 89% | +0% | +1% | 90% |
| repository | 95% | +0% | +0% | 95% |
| controller | 65% | +5% | +12% | 82% |
| client | 0% | +75% | +5% | 80% |
| health | 0% | +60% | +0% | 60% |

---

## 8. CI/CD Pipeline

### 8.1 Workflow Overview

```
┌─────────┐   ┌─────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────┐
│  Build  │──►│  Test   │──►│   Security  │──►│   Report    │──►│  Deploy │
│         │   │         │   │             │   │             │   │ (GitOps)│
└─────────┘   └────┬────┘   └──────┬──────┘   └─────────────┘   └─────────┘
                   │               │
          ┌────────┴────────┐      │
          │  Unit Tests     │      │
          │  Integration    │      │
          │  Functional     │      │
          └─────────────────┘      │
                                   │
                          ┌────────┴────────┐
                          │  SAST (CodeQL)  │
                          │  Dependency CVE │
                          │  DAST (ZAP)     │
                          └─────────────────┘
```

### 8.2 Quality Gates

| Gate | Threshold | Enforcement |
|------|-----------|-------------|
| Unit Test Coverage | 70% | CI fails if below |
| Integration Tests | All pass | Required for merge |
| Functional Tests | All pass | Required for merge |
| Dependency CVEs | No Critical/High | CI fails if found |
| CodeQL Alerts | No Critical | PR blocked |

---

## 9. Performance Characteristics

### 9.1 Rate Limiting

```
Per-merchant limits:
├── 1-second window: 10 req/s (burst protection)
└── 60-second window: 600 req/min (ceiling protection)

Memory: O(merchants) - one counter per merchant per window
```

### 9.2 Scalability

| Metric | Target | Mechanism |
|--------|--------|-----------|
| Throughput | 100 req/s | HPA auto-scaling |
| Latency P99 | < 200ms | Caching, efficient algorithms |
| Availability | 99.9% | Health probes, PDB, multi-replica |

### 9.3 Resource Efficiency

```
Container Optimizations:
├── Multi-stage Docker build (builder + runtime)
├── JRE 17 (not JDK) for smaller image
├── Non-root user for security
└── JVM tuning: -XX:MaxRAMPercentage=75.0
```

---

## 10. Future Enhancements

### 10.1 Short Term

| Enhancement | Priority | Technology |
|-------------|----------|------------|
| Distributed Tracing | P1 | OpenTelemetry + Jaeger |
| GitOps Deployment | P1 | ArgoCD |
| Secret Management | P2 | Sealed Secrets / Vault |
| Service Mesh | P2 | Istio |

### 10.2 Long Term

| Enhancement | Priority | Technology |
|-------------|----------|------------|
| Multi-region Deployment | P3 | Cross-cluster K8s |
| Chaos Engineering | P3 | Chaos Mesh |
| Serverless | P3 | Knative |

---

## 11. Comparison: Traditional vs Cloud-Native

| Dimension | Traditional (eBay-style) | Cloud-Native (This Project) |
|-----------|-------------------------|----------------------------|
| Build Tool | Maven (XML) | Gradle (DSL) |
| CI/CD | Jenkins (self-hosted) | GitHub Actions (managed) |
| Deployment | VM + manual steps | K8s + declarative YAML |
| Scaling | Manual ticket | HPA automatic |
| Observability | ELK (self-hosted ES) | Prometheus + Grafana |
| Load Testing | JMeter GUI + test env | k6 code + Docker |
| Configuration | Property files | ConfigMap + environment |
| Security | Manual audit | Automated SAST/DAST in CI |

---

## 12. Key Architectural Decisions

### ADR-001: "200-Only" API Design
**Status**: Accepted
**Context**: Payment APIs need predictable client behavior
**Decision**: Always return HTTP 200, encode business outcomes in body
**Consequences**: Simplified client logic, unified error handling

### ADR-002: In-Memory Storage
**Status**: Accepted
**Context**: Exercise scope, simplicity requirement
**Decision**: In-memory repository with dual indexing
**Consequences**: Data lost on restart, O(1) access, no persistence complexity

### ADR-003: AOP for Cross-Cutting Concerns
**Status**: Accepted
**Context**: Rate limiting and metrics should not pollute business code
**Decision**: Spring AOP aspects for rate limiting and metrics
**Consequences**: Clean business code, testable aspects, swappable implementations

### ADR-004: Kustomize over Helm
**Status**: Accepted
**Context**: Simple application, no complex templating needs
**Decision**: Kustomize for environment management
**Consequences**: Simpler than Helm, native to kubectl, no Tiller/Chart complexity

---

*Document Version: 1.0*
*Last Updated: 2026-05-27*
