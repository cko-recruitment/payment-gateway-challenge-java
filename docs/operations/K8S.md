# Kubernetes Deployment

## Prerequisites

- Kubernetes cluster (local via minikube/kind, or cloud)
- kubectl
- kustomize (built into kubectl 1.14+)

---

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         最终输出                                │
│                    (kubectl apply 实际应用的)                     │
│                                                                 │
│   Namespace: payment-gateway-prod                               │
│   Deployment: prod-payment-gateway (3 replicas)                 │
│   Service: prod-payment-gateway                                 │
│   Ingress: prod-payment-gateway                                 │
│   HPA: prod-payment-gateway (2-10 replicas)                     │
│   PDB: prod-payment-gateway (minAvailable: 1)                   │
│   ConfigMap: prod-payment-gateway-config                        │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │  Kustomize 合并 + 补丁
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                           │
        ▼                                           ▼
┌───────────────┐                        ┌──────────────────┐
│     Base      │                        │  Overlay (prod)  │
│               │                        │                  │
│ deployment    │◄───────────────────────│ replicas: 3      │
│   replicas: 2 │      继承所有资源        │ namespace: prod  │
│               │                        │ namePrefix: prod-│
│ service       │                        │ imageTag: 1.0.0  │
│   port: 80    │                        │ resources: 1Gi   │
│               │                        │                  │
│ ingress       │                        │ patch-resources  │
│   host: xxx   │                        │                  │
│               │                        │                  │
│ hpa           │                        │                  │
│   2-10        │                        │                  │
│               │                        │                  │
│ pdb           │                        │                  │
│   min: 1      │                        │                  │
│               │                        │                  │
│ configmap     │                        │                  │
│   bank.url    │                        │                  │
└───────────────┘                        └──────────────────┘
```

---

## Kustomize 三种变换方式

### 1. 直接覆盖（Declarative）

在 `kustomization.yaml` 中声明要覆盖的字段：

```yaml
namespace: payment-gateway-prod    # 给所有资源加 namespace
namePrefix: prod-                  # 给所有资源名加前缀
replicas:
  - name: payment-gateway
    count: 3                       # 覆盖 deployment 的 replicas
images:
  - name: payment-gateway
    newTag: "1.0.0"                # 覆盖镜像标签
```

### 2. Patch（局部修改）

只修改资源的某一部分，不改整个文件：

```yaml
# overlays/prod/patch-resources.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-gateway
spec:
  template:
    spec:
      containers:
        - name: payment-gateway
          resources:
            limits:
              memory: "1Gi"
              cpu: "1000m"
```

```yaml
# kustomization.yaml 中引用
patches:
  - path: patch-resources.yaml
```

### 3. 新增资源

在 Overlay 中引入 base 没有的资源：

```yaml
resources:
  - ../../base          # 继承 base
  - secret.yaml         # prod 环境特有的 Secret
```

---

## 目录结构

```
k8s/
├── base/                          # 共享的基础配置
│   ├── kustomization.yaml         # 定义 base 包含哪些资源
│   ├── configmap.yaml             # 应用配置（bank URL、JVM 参数）
│   ├── deployment.yaml            # Pod 模板（2 replicas）
│   ├── service.yaml               # ClusterIP Service
│   ├── ingress.yaml               # Ingress 外部访问
│   ├── hpa.yaml                   # 自动扩缩容（2-10）
│   └── pdb.yaml                   # 滚动更新保护
│
└── overlays/                      # 环境差异化配置
    ├── dev/
    │   ├── kustomization.yaml     # dev 环境：1 replica、latest 镜像
    │   └── patch-resources.yaml   # dev 资源限制（128Mi/256Mi）
    │
    └── prod/
        ├── kustomization.yaml     # prod 环境：3 replicas、1.0.0 镜像
        └── patch-resources.yaml   # prod 资源限制（512Mi/1Gi）
```

---

## 部署

### Dev 环境

```bash
# 预览生成的 YAML（不执行）
kubectl kustomize k8s/overlays/dev

# 应用
kubectl apply -k k8s/overlays/dev

# 检查状态
kubectl get pods -n payment-gateway-dev
kubectl get svc -n payment-gateway-dev
kubectl get ingress -n payment-gateway-dev
```

**Dev 环境配置：**

| 配置项 | 值 |
|--------|-----|
| Namespace | `payment-gateway-dev` |
| 资源前缀 | `dev-` |
| Replicas | 1 |
| 镜像 | `payment-gateway:latest` |
| 内存限制 | 128Mi / 256Mi |
| CPU 限制 | 100m / 250m |
| HPA | 不启用（replicas=1 < minReplicas=2） |

### Prod 环境

```bash
# 预览生成的 YAML
kubectl kustomize k8s/overlays/prod

# 应用
kubectl apply -k k8s/overlays/prod

# 检查状态
kubectl get pods -n payment-gateway-prod
kubectl get hpa -n payment-gateway-prod
```

**Prod 环境配置：**

| 配置项 | 值 |
|--------|-----|
| Namespace | `payment-gateway-prod` |
| 资源前缀 | `prod-` |
| Replicas | 3 |
| 镜像 | `payment-gateway:1.0.0` |
| 内存限制 | 512Mi / 1Gi |
| CPU 限制 | 500m / 1000m |
| HPA | 2-10 replicas，CPU > 70% 扩容 |
| PDB | 至少 1 个 Pod 可用 |

---

## 本地测试（Port Forward）

```bash
# Dev
kubectl port-forward -n payment-gateway-dev svc/dev-payment-gateway 8090:80

# Prod
kubectl port-forward -n payment-gateway-prod svc/prod-payment-gateway 8090:80

# 测试
curl http://localhost:8090/actuator/health
curl -X POST http://localhost:8090/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-1" \
  -H "Idempotency-Key: key-1" \
  -d '{"card_number":"4111111111111111","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}'
```

---

## 关键配置说明

### SecurityContext

```yaml
# Pod 级别：以非 root 用户运行
securityContext:
  runAsNonRoot: true
  runAsUser: 1000

# 容器级别：禁止特权提升
capabilities:
  drop: ["ALL"]
```

### Probe（健康检查）

| Probe | 作用 | 失败行为 |
|-------|------|---------|
| Liveness | 容器是否存活 | 失败则重启容器 |
| Readiness | 是否可接收流量 | 失败则从 Service 摘除 |

### HPA（自动扩缩容）

```yaml
minReplicas: 2          # 最少 2 个 Pod
maxReplicas: 10         # 最多 10 个 Pod
averageUtilization: 70  # CPU 超过 70% 时扩容
```

### PDB（Pod 中断预算）

```yaml
minAvailable: 1         # 滚动更新时至少保留 1 个 Pod
```

---

## 清理

```bash
kubectl delete -k k8s/overlays/dev
kubectl delete -k k8s/overlays/prod
```
