# Design Considerations and Assumptions

## Overview

This document describes the implementation of the Payment Gateway API and the core design principles ensuring consistency, security, and developer experience.

---

## 1. Architecture and Layering

The application follows a layered architecture to ensure separation of concerns and testability:

```
Controller (HTTP) → Service (Business Logic) → Repository (Storage)
       ↓                    ↓                        ↘
  RateLimitAspect    PaymentMetricsAspect         BankClient (External API)
```

**Key Responsibilities (following SOLID principles):**
- **`PaymentGatewayController`**: Thin layer for request routing and HTTP response orchestration. Maintains the "200-Only" API contract. Delegates all business logic to Service. *(Single Responsibility Principle)*
- **`PaymentGatewayApi`**: Interface containing all OpenAPI/Swagger annotations. Separates API documentation from implementation to keep Controller readable. *(Interface Segregation Principle)*
- **`PaymentGatewayService`**: The core engine for validation, bank interaction, and persistence orchestration. Depends on abstractions (`BankClient`, `IdempotencyRepository`) rather than concrete implementations. Handles business exceptions and converts them to domain responses. *(Dependency Inversion Principle)*
- **`IdempotencyRepository`**: Interface defining the contract for dual-indexed storage (System ID and Idempotency Key) with merchant isolation. `InMemoryIdempotencyRepository` provides the concrete in-memory implementation. *(Interface Segregation & Liskov Substitution Principles)*
- **`BankClient`**: Interface abstracting external bank communication, allowing the service to remain agnostic of the actual bank integration. `BankSimulatorClient` implements this for the simulator. *(Open/Closed Principle)*

---

## 2. API Design Principles

### Decision: The "200-Only" RESTful API

To provide a superior integration experience, the API avoids varied HTTP status codes for business or system outcomes. Instead, it uses **HTTP 200 OK** for all successful transport scenarios, conveying the actual result within a structured JSON body.

**Rationale:**
- **Simplified Client Logic**: Clients only need to handle a single response parsing flow.
- **Unified Error Model**: Every response (Success, Bank Decline, Validation Error, or System Crash) shares the same base structure.

### Decision: Unified `GatewayCode` Metadata

All API responses inherit from `BasePaymentResponse`, providing machine-readable outcome metadata:

| Field | Description | Example |
| :--- | :--- | :--- |
| `code` | Unique, machine-readable outcome code. | `PAYMENT_DECLINED` |
| `message` | Human-readable description of the result. | "Insufficient funds" |
| `action` | Recommended client action. | `none` |

**Standardized Gateway Codes:**
- `SUCCESS`: Operation completed as requested.
- `PAYMENT_DECLINED`: Bank refused the payment (Final state).
- `INVALID_REQUEST`: Validation failed or missing parameters (Final state).
- `BANK_UNAVAILABLE`: External bank downtime (Retryable).
- `PAYMENT_NOT_FOUND`: Retrieval requested for a non-existent resource.
- `INTERNAL_ERROR`: Unexpected gateway failure (Retryable).

---

## 3. Retrieval Strategy

### Decision: Dual Canonical URIs

The API supports two complementary retrieval paths, both returning a `GetPaymentResponse`:

1.  **`GET /api/v1/payments/{id}`**: Retrieval via system-generated UUID.
2.  **`GET /api/v1/payments/idempotency/{key}`**: Retrieval via the merchant's original reference (Idempotency Key).

**Contextual Security:**
Both endpoints require the `Merchant-Id` header. Data is scoped strictly to the provided merchant, ensuring that merchants cannot retrieve each other's data even if they guess an ID or Key.

---

## 4. Idempotency and Validation

### Decision: Hardened Idempotency with Concurrent Protection
`POST /api/v1/payments` uses the `Idempotency-Key` to ensure that duplicate requests from a merchant (e.g., due to network retries) do not result in multiple bank charges. 

**Key Features:**
- **Cached Responses**: Successful or declined responses are cached and returned for subsequent requests with the same key.
- **Retriable Errors**: If the previous attempt failed with a retriable error (e.g., `BANK_UNAVAILABLE`), the request is reprocessed instead of returning the cached error.
- **Concurrent Safety**: Uses `ConcurrentHashMap` with `ReentrantLock` per idempotency key to prevent race conditions when multiple identical requests arrive simultaneously.

### Decision: Strict ISO 4217 Validation
Currency validation is not merely format-based. The system utilizes `java.util.Currency` to ensure that every request contains a globally valid ISO currency code, preventing downstream failures at the bank level.

---

## 5. Error Handling Strategy

### Decision: Service Throws, Controller Translates

Following clean architecture principles, error handling responsibility is split by layer:

- **Service Layer**: Detects errors (validation failures, bank exceptions) and throws domain exceptions (`PaymentValidationException`, `BankProcessingException`).
- **Controller Layer**: Catches domain exceptions and translates them into `PostPaymentResponse` with appropriate `GatewayCode`.

**Rationale:**
- **Separation of Concerns**: Service knows business rules, Controller knows HTTP contract.
- **Testability**: Service tests verify exception throwing; Controller tests verify response mapping.
- **Consistency**: All error responses share the same JSON structure via `PostPaymentResponse.rejected()` / `bankErrorResponse()`.

---

## 6. Non-Functional Concerns (AOP)

### Decision: Cross-Cutting Concerns via Spring AOP
Rate limiting and metrics are implemented as aspects to avoid polluting business logic:

- **`RateLimitAspect`**: Multi-granularity fixed window (1-second + 60-second) per merchant. Uses `ConcurrentHashMap` with `AtomicInteger` — **O(merchants)** memory.
- **`PaymentMetricsAspect`**: Records payment latency and outcomes via SLF4J. Swappable for Micrometer/Prometheus later.

**Why AOP:** Follows SRP/OCP. Business code does not know about rate limiting or metrics.

---

## 7. Storage and Performance

### Decision: Dual-Indexed In-Memory Repository
The `InMemoryIdempotencyRepository` maintains two internal hash maps (System ID and Idempotency Key). This provides **O(1)** retrieval performance for all `GET` operations while keeping the architecture simple for the current scope.

---

## 8. Containerization and Deployment

### Decision: Docker-First Deployment

The application is packaged as a Docker image for consistent deployment across environments:

```
┌─────────────────────────────────────┐
│         Docker Image                 │
│  ┌─────────────────────────────┐    │
│  │  Eclipse Temurin JRE 17      │    │
│  │  ┌─────────────────────┐    │    │
│  │  │  Spring Boot App    │    │    │
│  │  │  (payment-gateway)  │    │    │
│  │  └─────────────────────┘    │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Key Features:**
- **Multi-stage build**: Separate build and runtime stages for smaller image size
- **Layered JAR**: Spring Boot layered JAR for efficient Docker layer caching
- **Health checks**: Built-in actuator endpoints for container health monitoring
- **Non-root user**: Security best practice

### Decision: Docker Compose for Local Development

```yaml
# docker-compose.yml
services:
  payment-gateway:
    build: .
    ports:
      - "8090:8090"
    environment:
      - BANK_URL=http://bank-simulator:8080/payments
  bank-simulator:
    image: bbyars/mountebank:2.8.1
```

### Decision: Kubernetes for Production

```
┌─────────────────────────────────────┐
│           Kubernetes                 │
│  ┌─────────┐  ┌─────────┐          │
│  │  Pod 1  │  │  Pod 2  │          │
│  │  + Envoy│  │  + Envoy│  ← Istio │
│  └─────────┘  └─────────┘          │
│       ↑              ↑              │
│    Service Mesh (Istio)             │
└─────────────────────────────────────┘
```

**K8s Features:**
- **Kustomize**: Base + overlay pattern for multi-environment (dev/prod)
- **Istio Service Mesh**: mTLS, traffic management, observability
- **HPA**: Horizontal Pod Autoscaler for automatic scaling
- **PDB**: Pod Disruption Budget for availability during updates
- **Ingress**: External traffic routing

---

## 9. Cloud-Native Features

### Decision: Infrastructure as Code (Terraform)

```hcl
# terraform/main.tf
module "eks" {
  source = "terraform-aws-modules/eks/aws"
  cluster_name = "payment-gateway"
  # ...
}
```

**Multi-Cloud Support:**
- AWS (EKS)
- Aliyun (ACK)
- Region selection based on data compliance requirements

### Decision: GitOps with ArgoCD

```yaml
# k8s/gitops/argocd-application.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: payment-gateway
spec:
  source:
    repoURL: https://github.com/james-yz-xie/payment-gateway-challenge-java
    path: k8s/overlays/prod
  syncPolicy:
    automated:
      selfHeal: true
      prune: true
```

### Decision: Comprehensive Observability

| Layer | Tool | Purpose |
|-------|------|---------|
| Metrics | Prometheus + Grafana | Performance monitoring |
| Tracing | Jaeger + OpenTelemetry | Distributed tracing |
| Logging | Structured JSON | Centralized logging |
| Alerts | Prometheus Alertmanager | Incident response |

---

## 10. CI/CD and Quality Gates

### Decision: GitHub Actions with Comprehensive Security Scanning

The project uses GitHub Actions for continuous integration with the following quality gates:

- **Build & Tests**: Compile and run Unit/Integration/Functional tests on every PR.
- **Coverage Report**: JaCoCo generates coverage reports, posted as PR comments.
- **Coverage Threshold**: 70% minimum for overall and changed files.
- **SAST**: CodeQL + OWASP Dependency-Check for static analysis.
- **DAST**: OWASP ZAP for dynamic API security scanning.
- **K8s Validation**: Kustomize build validation.

**Security Workflow:**
```
PR Push → SAST (CodeQL + Dependency-Check) → DAST (ZAP API Scan) → Report to PR
```

### Decision: Deployment Automation

**Manual Trigger Deployment:**
- Cloud provider selection (AWS/Aliyun)
- Environment selection (dev/staging/prod)
- Terraform plan review + manual approval for production
- Canary deployment with traffic splitting (1% → 100%)
- Automatic rollback on health check failure

---

## 11. Assumptions and Limitations

1.  **Volatility**: Data is lost on application restart (In-memory storage). Production would use Redis/Database.
2.  **Context Trust**: `Merchant-Id` is trusted from the header without cryptographic verification (Exercise scope).
3.  **Synchronous Flow**: Requests wait for the bank's response. Future versions would implement async webhooks.
4.  **Single Instance**: Rate limiting works within a single JVM. Production would use Redis for distributed rate limiting.

---

## 12. Design Trade-offs Summary

| Trade-off | Choice | Rationale |
| :--- | :--- | :--- |
| **Status Codes** | 200-Only | Predictable client parsing and business outcome focus. |
| **Response Model** | Inheritance | `GetPaymentResponse` extends `PostPaymentResponse` for semantic clarity without code duplication. |
| **Versioning** | Path (/v1) | Safe evolution and backward compatibility. |
| **Logging** | Masking | PII protection implemented via `toString()` overrides in models. |
| **Rate Limiting** | Multi-granularity Fixed Window | 1s (10 req) + 60s (600 req). Protects downstream while allowing bursts. |
| **Metrics** | AOP + SLF4J | Non-invasive. Upgrade to Micrometer without touching business code. |
| **Swagger** | Interface Separation | `PaymentGatewayApi` holds all OpenAPI annotations; `PaymentGatewayController` stays clean. |
| **Error Handling** | Service Throws / Controller Catches | Clean separation between business logic and HTTP representation. |
| **Coverage** | JaCoCo + PR Enforcement | 70% threshold with automatic PR comments; non-business code excluded. |
| **Deployment** | Docker + K8s + Istio | Cloud-native, scalable, observable. |
| **Security** | SAST + DAST + mTLS | Defense in depth. |
| **IaC** | Terraform | Multi-cloud, reproducible infrastructure. |
