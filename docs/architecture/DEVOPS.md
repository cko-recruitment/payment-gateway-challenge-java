# DevOps 职责边界

## 现实情况

```
以前（传统）：
┌─────────┐      ┌─────────┐      ┌─────────┐
│ 开发    │      │ 运维    │      │ 基础架构 │
│         │      │         │      │         │
│ 写代码   │  →  │ 部署    │  →  │ 配网络   │
│ 扔墙外   │      │ 接过来   │      │ 管机器   │
└─────────┘      └─────────┘      └─────────┘
     ↑                ↑                ↑
   各干各的，互相甩锅

现在（DevOps）：
┌─────────────────────────────────────┐
│         业务研发团队                 │
│  ┌─────────┐  ┌─────────┐          │
│  │ 写代码   │  │ 写部署   │          │
│  │ 写测试   │  │ 写监控   │          │
│  │ 写配置   │  │ 写告警   │          │
│  └─────────┘  └─────────┘          │
│       ↑              ↑              │
│    同一个人负责全链路                │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│         平台团队（基础架构）          │
│  ┌─────────┐  ┌─────────┐          │
│  │ 提供 K8s │  │ 提供监控 │          │
│  │ 提供模板 │  │ 提供工具 │          │
│  │ 提供规范 │  │ 提供培训 │          │
│  └─────────┘  └─────────┘          │
│       ↑              ↑              │
│    提供平台能力，不管具体业务配置      │
└─────────────────────────────────────┘
```

## 职责划分

| 任务 | 业务研发团队 | 平台团队 |
|-----|------------|---------|
| **写业务代码** | ✅ | ❌ |
| **写 Dockerfile** | ✅ | ❌ |
| **写 K8s YAML** | ✅ | ❌ |
| **配置监控告警** | ✅ | ❌ |
| **配置 Istio 路由** | ✅（用模板） | ❌ |
| **维护 K8s 集群** | ❌ | ✅ |
| **维护 Istio 控制面** | ❌ | ✅ |
| **提供部署模板** | ❌ | ✅ |
| **提供 CI/CD 工具** | ❌ | ✅ |
| **安全策略制定** | ❌ | ✅ |

## 平台团队提供的模板

```yaml
# 平台团队提供：base-template.yaml
# 业务团队填空即可

apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.service.name }}
spec:
  replicas: {{ .Values.service.replicas }}
  template:
    spec:
      containers:
        - name: app
          image: {{ .Values.service.image }}
          resources:
            # 平台团队预设：根据服务等级自动分配
            requests:
              memory: {{ .Values.service.resources.requests.memory }}
              cpu: {{ .Values.service.resources.requests.cpu }}
            limits:
              memory: {{ .Values.service.resources.limits.memory }}
              cpu: {{ .Values.service.resources.limits.cpu }}
          # 平台团队预设：健康检查标准
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8090
            initialDelaySeconds: 60
            periodSeconds: 20
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8090
            initialDelaySeconds: 30
            periodSeconds: 10
```

## 业务团队只需填

```yaml
# values.yaml（业务团队写）
service:
  name: payment-gateway
  image: payment-gateway:v1.2.3
  replicas: 3
  
  # 选择服务等级（平台团队预定义）
  tier: critical  # critical / standard / batch
  
  # 业务自定义（可选）
  env:
    - name: BANK_URL
      value: http://bank-simulator:8080/payments
```

## 平台团队预定义的服务等级

```yaml
# platform-defaults.yaml（平台团队维护）
tiers:
  critical:
    resources:
      requests:
        memory: "512Mi"
        cpu: "500m"
      limits:
        memory: "1Gi"
        cpu: "1000m"
    replicas: 3
    pdb:
      minAvailable: 2
    istio:
      retry: 2
      timeout: 5s
      circuitBreaker:
        consecutiveErrors: 5
        
  standard:
    resources:
      requests:
        memory: "256Mi"
        cpu: "250m"
      limits:
        memory: "512Mi"
        cpu: "500m"
    replicas: 2
    pdb:
      minAvailable: 1
    istio:
      retry: 1
      timeout: 10s
      circuitBreaker:
        consecutiveErrors: 10
        
  batch:
    resources:
      requests:
        memory: "128Mi"
        cpu: "100m"
      limits:
        memory: "256Mi"
        cpu: "250m"
    replicas: 1
    pdb:
      minAvailable: 0
    istio:
      retry: 0
      timeout: 60s
```

## 协作流程

```
1. 平台团队提供模板和工具
        ↓
2. 业务团队用模板写配置
        ↓
3. 提交 PR，双方 Review
        ↓
4. CI 自动验证（平台团队写的检查）
        ↓
5. CD 自动部署
```

## 面试回答

> "DevOps 不是让开发干运维的活，而是平台团队提供标准化模板和工具，业务团队专注业务配置。比如平台团队定义 critical/standard/batch 三个服务等级，业务团队只需要选等级、填镜像名，不用关心 CPU 限制和熔断阈值怎么配。"
