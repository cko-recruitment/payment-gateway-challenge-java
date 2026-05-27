# Chaos Engineering with Chaos Mesh

## Overview

Chaos Mesh is a cloud-native Chaos Engineering platform that orchestrates chaos experiments in Kubernetes environments.

## Philosophy

> "Chaos Engineering is the discipline of experimenting on a system in order to build confidence in the system's capability to withstand turbulent conditions in production."

## Experiments

### 1. Pod Kill (`pod-kill.yaml`)

**Purpose**: Verify system resilience when pods are killed

**Scenario**: Kubernetes node failure, OOM kill, eviction

**Expected Behavior**:
- HPA creates new pod within 30s
- PDB ensures at least 1 pod available
- No payment requests lost (in-flight requests may fail)

```bash
# Run experiment
kubectl apply -f k8s/chaos/pod-kill.yaml

# Monitor
kubectl get pods -n payment-gateway-prod -w
kubectl get events -n payment-gateway-prod
```

### 2. Network Delay (`network-delay.yaml`)

**Purpose**: Test latency tolerance

**Scenario**: Cross-AZ traffic, network congestion

**Expected Behavior**:
- P99 latency increases but stays < 500ms
- Circuit breaker opens if latency exceeds threshold
- Client retries with exponential backoff

```bash
# Run experiment
kubectl apply -f k8s/chaos/network-delay.yaml

# Monitor latency
kubectl exec -it deploy/prometheus -n observability -- \
  curl 'localhost:9090/api/v1/query?query=histogram_quantile(0.99,rate(payment_latency_seconds_bucket[5m]))'
```

### 3. Network Partition (`network-partition.yaml`)

**Purpose**: Test bank simulator unavailability

**Scenario**: Bank API outage, network partition

**Expected Behavior**:
- Payment requests return `BANK_UNAVAILABLE` code
- Retryable errors trigger client retry
- Queue-based processing (if implemented)

```bash
# Run experiment
kubectl apply -f k8s/chaos/network-partition.yaml

# Test payment
kubectl exec -it deploy/payment-gateway -n payment-gateway-prod -- \
  curl -X POST localhost:8090/api/v1/payments \
    -H "Merchant-Id: test" \
    -H "Idempotency-Key: chaos-test"
```

### 4. CPU Stress (`stress-test.yaml`)

**Purpose**: Test resource limits and HPA

**Scenario**: Traffic spike, resource exhaustion

**Expected Behavior**:
- CPU usage approaches limit (500m)
- HPA scales up to max replicas (10)
- No OOM kills (memory stays within limits)

```bash
# Run experiment
kubectl apply -f k8s/chaos/stress-test.yaml

# Monitor scaling
kubectl get hpa -n payment-gateway-prod -w
kubectl top pods -n payment-gateway-prod
```

### 5. HTTP Abort (`http-abort.yaml`)

**Purpose**: Test API failure handling

**Scenario**: Application crash, panic

**Expected Behavior**:
- Failed requests return 500
- Client receives appropriate error code
- Metrics track error rate spike

```bash
# Run experiment
kubectl apply -f k8s/chaos/http-abort.yaml

# Monitor error rate
kubectl exec -it deploy/prometheus -n observability -- \
  curl 'localhost:9090/api/v1/query?query=rate(payment_total{status="REJECTED"}[1m])'
```

## Installation

```bash
# Install Chaos Mesh
curl -sSL https://mirrors.chaos-mesh.org/latest/install.sh | bash

# Verify
kubectl get pods -n chaos-testing

# Create namespace for experiments
kubectl create namespace chaos-testing
```

## Running Experiments

```bash
# Run all experiments
kubectl apply -f k8s/chaos/

# Check experiment status
kubectl get podchaos -n chaos-testing
kubectl get networkchaos -n chaos-testing
kubectl get stresschaos -n chaos-testing

# View experiment details
kubectl describe podchaos payment-gateway-pod-kill -n chaos-testing
```

## Monitoring During Chaos

```bash
# Watch pods
kubectl get pods -n payment-gateway-prod -w

# Watch HPA
kubectl get hpa -n payment-gateway-prod -w

# Watch metrics
kubectl port-forward svc/prometheus -n observability 9090:9090
# Open http://localhost:9090

# Watch logs
kubectl logs -f deploy/payment-gateway -n payment-gateway-prod
```

## Cleanup

```bash
# Stop all experiments
kubectl delete -f k8s/chaos/

# Uninstall Chaos Mesh
curl -sSL https://mirrors.chaos-mesh.org/latest/install.sh | bash -s -- --template | kubectl delete -f -
```

## Game Days

Schedule regular chaos engineering exercises:

1. **Weekly**: Automated pod-kill during off-peak
2. **Monthly**: Network partition during business hours
3. **Quarterly**: Full chaos day with all experiments

## Success Criteria

| Experiment | Metric | Threshold |
|-----------|--------|-----------|
| Pod Kill | Recovery Time | < 60s |
| Network Delay | P99 Latency | < 500ms |
| Partition | Error Rate | < 1% |
| CPU Stress | HPA Scale Up | < 2min |
| HTTP Abort | Error Detection | < 5s |
