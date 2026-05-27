# Istio Service Mesh

## Overview

Istio provides traffic management, security, and observability for the Payment Gateway microservices.

## Components

### 1. Gateway (`gateway.yaml`)

Entry point for external traffic:
- HTTP → HTTPS redirect
- TLS termination
- Host-based routing

### 2. VirtualService (`virtualservice.yaml`)

Traffic routing rules:
- **Retries**: 3 attempts with 2s timeout per try
- **Timeout**: 10s total
- **Fault Injection**: 0.1% delay for chaos testing

### 3. DestinationRule (`destinationrule.yaml`)

Load balancing and circuit breaker:
- **Load Balancer**: LEAST_CONN
- **Circuit Breaker**: Eject after 5 consecutive errors
- **Subsets**: stable/canary for blue-green deployment

### 4. PeerAuthentication (`peerauthentication.yaml`)

mTLS enforcement:
- STRICT mode: All traffic encrypted with mTLS

### 5. AuthorizationPolicy (`authorizationpolicy.yaml`)

Access control:
- Ingress gateway → API endpoints
- Internal → Health/metrics endpoints only

## Installation

```bash
# Install Istio
istioctl install --set profile=default -y

# Enable sidecar injection
kubectl label namespace payment-gateway-prod istio-injection=enabled

# Apply configurations
kubectl apply -f k8s/istio/
```

## Traffic Management

### Canary Deployment

```bash
# Route 90% to stable, 10% to canary
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: payment-gateway
spec:
  hosts:
    - payment-gateway
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

### Circuit Breaker Test

```bash
# Simulate failures
kubectl exec -it deploy/payment-gateway -- curl localhost:8090/actuator/health

# Check ejected hosts
istioctl proxy-config cluster deploy/payment-gateway -n payment-gateway-prod
```

## Observability

Istio automatically collects:
- **Metrics**: Prometheus (request count, latency, errors)
- **Tracing**: Jaeger (distributed traces)
- **Logs**: Access logs for all traffic

```bash
# View Istio metrics
kubectl exec -it deploy/prometheus -n istio-system -- curl localhost:9090/api/v1/query?query=istio_requests_total

# View distributed traces
kubectl port-forward svc/jaeger -n istio-system 16686:16686
```
