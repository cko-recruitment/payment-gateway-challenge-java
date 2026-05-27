# GitOps with ArgoCD

## Overview

This directory contains ArgoCD configurations for GitOps-based deployment of the Payment Gateway application.

## Architecture

```
┌─────────────────┐     Git Push      ┌─────────────────┐
│   Developer     │ ────────────────► │   GitHub Repo   │
│                 │   (update image)  │                 │
└─────────────────┘                   └────────┬────────┘
                                               │
                                               │ ArgoCD watches
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │     ArgoCD      │
                                      │   (GitOps)      │
                                      └────────┬────────┘
                                               │
                                               │ Auto-sync
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │   Kubernetes    │
                                      │   (Prod Env)    │
                                      └─────────────────┘
```

## Components

### 1. Application (`argocd-application.yaml`)

Defines the GitOps application that ArgoCD manages:
- **Source**: GitHub repo `k8s/overlays/prod`
- **Destination**: `payment-gateway-prod` namespace
- **Sync Policy**: Automated with self-healing
- **Prune**: Remove resources not in Git

### 2. AppProject (`argocd-appproject.yaml`)

Defines project boundaries and permissions:
- Allowed source repositories
- Allowed destination namespaces
- Resource whitelist/blacklist

### 3. Image Updater (`argocd-image-updater.yaml`)

Automatically updates container images:
- Watches Docker registry for new tags
- Updates Git repo with new image tag
- ArgoCD syncs the change to cluster

## Setup

### Install ArgoCD

```bash
# Create namespace
kubectl create namespace argocd

# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Access UI
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

### Configure Application

```bash
# Apply project
kubectl apply -f k8s/gitops/argocd-appproject.yaml

# Apply application
kubectl apply -f k8s/gitops/argocd-application.yaml
```

### Configure Image Updater

```bash
# Install ArgoCD Image Updater
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/manifests/install.yaml

# Configure Git write-back
kubectl create secret generic argocd-image-updater-git-creds \
  --namespace argocd \
  --from-literal=username=<github-username> \
  --from-literal=password=<github-token>
```

## Workflow

### Manual Deployment

```bash
# Update image tag in k8s/overlays/prod/kustomization.yaml
# Commit and push
kubectl apply -k k8s/overlays/prod
```

### Automated Deployment (GitOps)

```
1. CI builds and pushes new image (e.g., v1.2.3)
2. Image Updater detects new tag
3. Image Updater updates kustomization.yaml in Git
4. ArgoCD detects Git change
5. ArgoCD syncs to Kubernetes
```

## Monitoring

```bash
# Check sync status
argocd app get payment-gateway

# View sync history
argocd app history payment-gateway

# Rollback to previous version
argocd app rollback payment-gateway <revision>
```

## Security

- ArgoCD uses RBAC for access control
- Git repo is the single source of truth
- All changes are audited in Git history
- No manual kubectl access required
