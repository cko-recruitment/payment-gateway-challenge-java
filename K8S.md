# Kubernetes Deployment

## Prerequisites

- Kubernetes cluster (local via minikube/kind, or cloud)
- kubectl
- kustomize (built into kubectl 1.14+)

---

## Deploy to Dev

```bash
# Apply dev overlay (1 replica)
kubectl apply -k k8s/overlays/dev

# Check status
kubectl get pods -n payment-gateway-dev
kubectl get svc -n payment-gateway-dev
```

## Deploy to Prod

```bash
# Apply prod overlay (3 replicas)
kubectl apply -k k8s/overlays/prod
```

## Port Forward (Local Testing)

```bash
kubectl port-forward -n payment-gateway-dev svc/dev-payment-gateway 8090:80

# Test
curl http://localhost:8090/api/v1/payments \
  -H "Merchant-Id: merchant-1"
```

## Structure

```
k8s/
├── base/                  # Shared manifests
│   ├── deployment.yaml    # Deployment with 2 replicas
│   ├── service.yaml       # ClusterIP service
│   └── kustomization.yaml
├── overlays/
│   ├── dev/               # Dev environment (1 replica)
│   └── prod/              # Prod environment (3 replicas)
```

## Cleanup

```bash
kubectl delete -k k8s/overlays/dev
kubectl delete -k k8s/overlays/prod
```
