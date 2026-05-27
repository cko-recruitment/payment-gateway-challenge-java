# Terraform - Payment Gateway Infrastructure

Minimal viable infrastructure for demo purpose.

## Architecture

```
┌─────────────────────────────────────┐
│              VPC (10.0.0.0/16)       │
│  ┌─────────────┐  ┌─────────────┐   │
│  │ Public Subnet│  │ Public Subnet│   │  ← ALB, NAT Gateway
│  │ 10.0.101.0/24│  │ 10.0.102.0/24│   │
│  └─────────────┘  └─────────────┘   │
│  ┌─────────────┐  ┌─────────────┐   │
│  │Private Subnet│  │Private Subnet│   │  ← EKS Nodes, Pods
│  │ 10.0.1.0/24  │  │ 10.0.2.0/24  │   │
│  └─────────────┘  └─────────────┘   │
│           ┌─────────┐               │
│           │  EKS    │               │
│           │ Cluster │               │
│           └─────────┘               │
└─────────────────────────────────────┘
```

## Usage

```bash
# 1. Initialize
cd terraform
terraform init

# 2. Plan (preview changes)
terraform plan

# 3. Apply (create infrastructure)
terraform apply

# 4. Configure kubectl
terraform output configure_kubectl
# Run the command to connect to cluster

# 5. Deploy application
kubectl apply -k ../k8s/overlays/dev
```

## Files

| File | Purpose |
|------|---------|
| `main.tf` | VPC + EKS resources |
| `variables.tf` | Input variables |
| `outputs.tf` | Output values |

## Cost

- EKS Control Plane: ~$0.10/hour (~$73/month)
- t3.medium Spot: ~$0.015/hour (~$11/month)
- NAT Gateway: ~$0.045/hour (~$33/month)
- **Total: ~$120/month**

## Cleanup

```bash
terraform destroy
```
